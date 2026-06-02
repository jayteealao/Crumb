package com.github.jayteealao.twitter.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.github.jayteealao.crumbs.data.FilterState
import com.github.jayteealao.crumbs.data.TypeFilter
import com.github.jayteealao.twitter.data.Repository
import com.github.jayteealao.twitter.data.SnackbarEvent
import com.github.jayteealao.twitter.data.SyncStatusRepository
import com.github.jayteealao.twitter.data.dto.SyncStatus
import com.github.jayteealao.twitter.models.TweetData
import dagger.hilt.android.lifecycle.HiltViewModel
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
