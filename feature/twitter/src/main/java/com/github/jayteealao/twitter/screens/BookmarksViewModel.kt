package com.github.jayteealao.twitter.screens

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.crumbs.data.FilterState
import com.github.jayteealao.crumbs.data.TypeFilter
import com.github.jayteealao.twitter.data.Repository
import com.github.jayteealao.twitter.data.SnackbarEvent
import com.github.jayteealao.twitter.data.SyncStatusRepository
import com.github.jayteealao.twitter.data.dto.SyncStatus
import com.github.jayteealao.twitter.models.TweetData
import com.github.jayteealao.twitter.player.VariantSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BookmarksViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    private val repository: Repository,
    private val syncStatusRepository: SyncStatusRepository,
) : ViewModel() {

    init {
        // Kick off an initial sync_status read so the reconnect banner can
        // render before the user touches anything. Server-side polling owns
        // the actual bookmark fetching post-cutover.
        viewModelScope.launch { syncStatusRepository.refresh() }
    }

    val isRefreshing: StateFlow<Boolean> = repository.isRefreshing

    val syncStatus: StateFlow<SyncStatus?> = syncStatusRepository.flow

    val snackbarEvents: SharedFlow<SnackbarEvent> = repository.snackbarEvents

    private val _filter = MutableStateFlow(FilterState())
    val filter: StateFlow<FilterState> = _filter.asStateFlow()

    val pagingFlow: Flow<PagingData<TweetData>> = _filter
        .flatMapLatest { state -> repository.pagingTweetData(state) }
        .cachedIn(viewModelScope)

    /**
     * Live count of the current Twitter feed for the SAVED header. Re-derives on every
     * [_filter] change (tag or type) via the same repository source the paging feed uses,
     * so the header and the visible list stay in lockstep. `stateIn` + `WhileSubscribed`
     * keeps the underlying count query observed only while the UI is on-screen; seeds `0`
     * (which the header renders as `000`) until the first emission.
     */
    val itemCount: StateFlow<Int> = _filter
        .flatMapLatest { state -> repository.countFlow(state) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun pagingFlowData(order: String = "default"): Flow<PagingData<TweetData>> = pagingFlow

    fun refresh() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.refreshBookmarks()
                // Re-read sync_status so a freshly-linked user sees lastPolledAt
                // advance without waiting for the next foreground transition.
                syncStatusRepository.refresh(force = true)
            }
        }
    }

    fun refreshSyncStatus() {
        viewModelScope.launch { syncStatusRepository.refresh() }
    }

    // Tweet ids already attempted this session. Keeps a media-less card re-entering
    // composition (scroll off + back) from re-hitting Firestore every time. Accessed
    // only from the main thread (composition + viewModelScope's default dispatcher).
    private val mediaRefetchAttempts = mutableSetOf<String>()

    /**
     * Lazy on-view media re-fetch (image-rendering AC). Attempts at most one
     * re-fetch per tweet per session; on success Room invalidation re-emits the
     * paged card with its images. A tweet with no media in Firestore settles to
     * text-only and is not retried until the next session (the one-time backfill
     * worker repairs the back-catalogue in bulk) — this bound keeps the feed from
     * hammering Firestore with a single-doc read for every text card on each scroll.
     */
    fun refetchMediaIfMissing(tweetId: String) {
        if (!mediaRefetchAttempts.add(tweetId)) return
        viewModelScope.launch {
            runCatching { repository.refetchTweetMedia(tweetId) }
        }
    }

    // ---- Inline video (single shared, leak-free ExoPlayer) ----
    //
    // The feed is the only video surface, so one player scoped to this ViewModel is
    // enough: it is created lazily on first play, survives config changes (the VM
    // outlives recomposition), and is released in [onCleared] (frees the decoder lease
    // when the user leaves the feed). A `@Singleton` app-wide player was rejected (it
    // would hold a decoder off-screen) and a `remember`ed composable player was rejected
    // (lost across config changes). All player mutation + [release] happens on the main
    // thread (these methods are called from composition / lifecycle callbacks) — never
    // from a background dispatcher, which is the most common Media3 wrong-thread crash.

    private var exoPlayer: ExoPlayer? = null

    /** Tweet id of the single active video card, or null when none is playing inline. */
    private val _activeVideoId = MutableStateFlow<String?>(null)
    val activeVideoId: StateFlow<String?> = _activeVideoId.asStateFlow()

    /** True while the full-screen video viewer is open (it borrows the same shared player). */
    private val _videoViewerOpen = MutableStateFlow(false)
    val videoViewerOpen: StateFlow<Boolean> = _videoViewerOpen.asStateFlow()

    /** Whether playback was running when the app backgrounded, so ON_RESUME can restore it. */
    private var wasPlayingBeforePause = false

    /** The shared player for the UI to bind to the active card / viewer surface. Null until first play. */
    val player: ExoPlayer? get() = exoPlayer

    private fun obtainPlayer(): ExoPlayer = exoPlayer ?: ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .also { exoPlayer = it }

    /**
     * Make [bookmark] the single active inline video and start playback. Selects the best
     * stream (HLS → DASH → highest-bitrate MP4) via [VariantSelection]; falls back to the
     * flat [Bookmark.videoUrl]. No playable source → no-op (the card stays on its poster).
     */
    fun playVideo(bookmark: Bookmark) {
        val item = VariantSelection.toMediaItem(bookmark.videoVariants)
            ?: bookmark.videoUrl?.let { MediaItem.fromUri(it) }
            ?: return
        val p = obtainPlayer()
        p.setMediaItem(item)
        p.prepare()
        p.playWhenReady = true
        _activeVideoId.value = bookmark.id
    }

    /** Open the full-screen viewer for [bookmark], starting playback first if it is not already active. */
    fun expandVideo(bookmark: Bookmark) {
        if (_activeVideoId.value != bookmark.id) playVideo(bookmark)
        _videoViewerOpen.value = true
    }

    fun closeVideoViewer() {
        _videoViewerOpen.value = false
    }

    /**
     * The active video card scrolled out of view: detach the surface and stop playback so a
     * scrolling feed keeps at most one live, audible player. The player instance is retained
     * (a later tap re-uses it); it is fully released only in [onCleared].
     */
    fun clearActiveVideo() {
        val p = exoPlayer ?: return
        p.playWhenReady = false
        p.clearVideoSurface()
        _activeVideoId.value = null
    }

    /** Lifecycle ON_PAUSE: pause, remembering whether to resume on ON_RESUME. */
    fun onAppPaused() {
        val p = exoPlayer ?: return
        wasPlayingBeforePause = p.isPlaying
        p.pause()
    }

    /** Lifecycle ON_RESUME: resume only if playback was running when we backgrounded. */
    fun onAppResumed() {
        val p = exoPlayer ?: return
        if (wasPlayingBeforePause) p.play()
    }

    override fun onCleared() {
        super.onCleared()
        // Main-thread release — frees the decoder lease when the feed VM is destroyed.
        exoPlayer?.release()
        exoPlayer = null
    }

    fun onTypeChipToggled(typeId: String) {
        val next = runCatching { TypeFilter.valueOf(typeId.uppercase()) }.getOrDefault(TypeFilter.ALL)
        _filter.update { it.copy(type = next) }
    }

    fun onTagToggled(tag: String) {
        _filter.update {
            val tags = if (tag in it.selectedTags) it.selectedTags - tag else it.selectedTags + tag
            it.copy(selectedTags = tags.toPersistentSet())
        }
    }

    fun onTagsApplied(tags: Set<String>) {
        _filter.update { it.copy(selectedTags = tags.toPersistentSet()) }
    }

    fun clearTagFilter() {
        _filter.update { it.copy(selectedTags = persistentSetOf()) }
    }

    fun softDelete(id: String) {
        viewModelScope.launch { repository.softDelete(id) }
    }

    fun undoDelete(id: String) {
        viewModelScope.launch { repository.undoDelete(id) }
    }

    fun confirmDeletePending(id: String) {
        viewModelScope.launch { repository.confirmDeletePending(id) }
    }

    fun cancelDeletePending(id: String) {
        viewModelScope.launch { repository.cancelDeletePending(id) }
    }

    // Tag operations
    private val _tagsForTweet = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val tagsForTweet: StateFlow<Map<String, List<String>>> = _tagsForTweet

    private val _allTags = MutableStateFlow<List<String>>(emptyList())
    val allTags: StateFlow<List<String>> = _allTags

    init {
        loadAllTags()
    }

    fun loadTagsForTweet(tweetId: String) {
        viewModelScope.launch {
            val tags = repository.getTagsForTweet(tweetId)
            // .update is an atomic CAS — two concurrent single-item loads
            // can no longer lose each other's writes (the previous
            // `value = value + pair` was a read-modify-write race).
            _tagsForTweet.update { it + (tweetId to tags) }
        }
    }

    fun loadTagsForItems(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val batch = repository.getTagsForItems(ids)
            // Same atomic merge — preserves existing entries not in the
            // batch so single-item saveTags() updates are not overwritten.
            _tagsForTweet.update { it + batch }
        }
    }

    fun loadAllTags() {
        viewModelScope.launch {
            _allTags.value = repository.getAllTags()
        }
    }

    fun saveTags(tweetId: String, tags: List<String>) {
        viewModelScope.launch {
            repository.saveTags(tweetId, tags)
            loadTagsForTweet(tweetId)
            loadAllTags()
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }

    private val _disconnectEvents = MutableSharedFlow<DisconnectEvent>(replay = 0, extraBufferCapacity = 1)
    val disconnectEvents: SharedFlow<DisconnectEvent> = _disconnectEvents

    fun disconnectX() {
        viewModelScope.launch {
            repository.disconnectX()
                .onSuccess {
                    syncStatusRepository.refresh(force = true)
                    _disconnectEvents.tryEmit(DisconnectEvent.Success)
                }
                .onFailure { e ->
                    _disconnectEvents.tryEmit(DisconnectEvent.Failure(e.message ?: "disconnect_failed"))
                }
        }
    }
}

sealed interface DisconnectEvent {
    data object Success : DisconnectEvent
    data class Failure(val reason: String) : DisconnectEvent
}
