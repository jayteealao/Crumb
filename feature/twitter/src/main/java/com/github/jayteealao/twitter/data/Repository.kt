package com.github.jayteealao.twitter.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.data.DeletedBookmarkRepository
import com.github.jayteealao.crumbs.data.FilterState
import com.github.jayteealao.crumbs.data.TagRepository
import com.github.jayteealao.twitter.data.firestore.FirestoreRepository
import com.github.jayteealao.twitter.models.TagEntity
import com.github.jayteealao.twitter.models.TweetData
import com.github.jayteealao.twitter.models.TweetEntities
import com.github.jayteealao.twitter.models.TweetEntity
import com.github.jayteealao.twitter.models.TweetTagCrossRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Repository @Inject constructor(
    private val tweetDao: TweetDao,
    private val authPref: Prefs,
    private val firestoreRepository: FirestoreRepository,
    private val deletedBookmarkRepository: DeletedBookmarkRepository,
    private val callableService: TwitterCallableService,
    private val scope: CoroutineScope,
    private val syncEnqueuer: TwitterSyncEnqueuer,
) : TagRepository {
    private var latestBookmarkInDatabase: TweetEntity? = null
    private var orderOfLastBookmark: Int = 1000
    private val fetchMutex = Mutex()

    private val _isRefreshing = MutableStateFlow(false)
    /**
     * Combines the brief in-flight `triggerPoll` flag with the long-running
     * WorkManager state for the unique sync worker. The UI spinner therefore
     * stays up across backgrounding, recreation, and process death — driven
     * by `WorkInfo.State.{ENQUEUED, RUNNING}` for the duration of the worker
     * + the local `_isRefreshing` window while we wait on `triggerPoll`.
     */
    val isRefreshing: StateFlow<Boolean> = combine(
        _isRefreshing.asStateFlow(),
        syncEnqueuer.observeIsRunning(),
    ) { triggering, syncing -> triggering || syncing }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    private val _snackbarEvents = MutableSharedFlow<SnackbarEvent>(replay = 0, extraBufferCapacity = 4)
    val snackbarEvents: SharedFlow<SnackbarEvent> = _snackbarEvents.asSharedFlow()

    companion object {
        const val BUFFER = 250
    }

    init {
        scope.launch(Dispatchers.IO) {
            try {
                latestBookmarkInDatabase = tweetDao.getLatestBookmark()
                Timber.d("latest bookmark in database: $latestBookmarkInDatabase")
                if (latestBookmarkInDatabase != null) {
                    orderOfLastBookmark = latestBookmarkInDatabase!!.order
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in Repository init")
            }
        }
    }

    fun saveTweetEntities(tweetEntities: TweetEntities, uploadToFirestore: Boolean = true) {
        tweetDao.insertTweetEntitiesAtomic(
            tweetEntities.tweetEntity,
            tweetEntities.tweetReferencedTweets.mapNotNull { it.tweet },
            tweetEntities.twitterUserEntity,
            tweetEntities.tweetPublicMetrics,
            tweetEntities.tweetMediaEntity,
            tweetEntities.tweetIncludesEntity,
            tweetEntities.tweetReferencedTweets.map { it.referencedTweets },
            tweetEntities.tweetContextAnnotationEntity,
            tweetEntities.tweetTextEntity,
            tweetEntities.mediaKeys,
            tweetEntities.pollIds,
        )
        // Also upload to Firestore for backup
        if (uploadToFirestore) {
            scope.launch(Dispatchers.IO) {
                firestoreRepository.uploadTweet(tweetEntities)
            }
        }
    }

    /**
     * Single-tweet media re-fetch for the legacy (pre-cutover) corpus. Re-pulls the
     * tweet's full entity set from Firestore and writes it through the existing
     * IGNORE-on-conflict atomic insert: the already-present tweet/user/metrics rows
     * are no-ops, so only the missing media (+ text annotations + media keys) land.
     * Idempotent — safe to call repeatedly and from the backfill worker. Returns
     * true when the re-fetched tweet carried any media rows.
     *
     * Drives the card's retry-on-revisit: on a successful media insert Room's
     * InvalidationTracker re-emits the paged card with its images. Does NOT re-upload
     * to Firestore and does NOT touch the includes-drop block (quoted-tweet FKs are
     * owned by the quoted-tweets slice).
     */
    suspend fun refetchTweetMedia(tweetId: String): Boolean = withContext(Dispatchers.IO) {
        val entities = firestoreRepository.fetchSingleTweetEntities(tweetId)
            ?: return@withContext false
        if (entities.tweetMediaEntity.isEmpty()) return@withContext false
        saveTweetEntities(entities, uploadToFirestore = false)
        true
    }

    /**
     * Pull-to-refresh entry point. Calls the server-side `triggerPoll` callable
     * to wake the daily-poll function, then enqueues the local
     * `TwitterSyncWorker` so any newly-arrived Firestore docs are streamed
     * into Room. The worker is enqueued unique-by-uid with `KEEP` policy so
     * back-to-back pull gestures coalesce instead of stacking.
     *
     * The `isRefreshing` state surfaced to the UI is driven by the worker's
     * `WorkInfo` (see `BookmarksViewModel`) so it survives backgrounding and
     * activity recreation. This method's `_isRefreshing` flag only covers the
     * brief `triggerPoll` callable window; once the callable returns and the
     * worker is enqueued, the WorkInfo flow takes over.
     *
     * `triggerPoll` failures still flow through `_snackbarEvents` so the UI
     * can surface "debounced" / "in progress" / generic-failure copy.
     */
    suspend fun refreshBookmarks() {
        if (!fetchMutex.tryLock()) {
            Timber.d("refreshBookmarks: another refresh in flight, skipping")
            return
        }
        _isRefreshing.value = true
        try {
            val result = runCatching { callableService.triggerPoll() }
            // Always enqueue the local sync, even if triggerPoll failed — the
            // prior poll (e.g., the oauthCallback fan-out) may have written
            // docs the device hasn't synced locally yet. This is the recovery
            // path when cold-start enqueue raced Firebase Auth restoration.
            syncEnqueuer.enqueueRefresh()

            val payload = result.getOrNull()
            if (payload == null) {
                Timber.w("triggerPoll returned no payload; assuming failure")
                _snackbarEvents.tryEmit(
                    SnackbarEvent.GenericFailure(result.exceptionOrNull()?.message ?: "no_response")
                )
                return
            }

            val ok = payload["ok"] as? Boolean ?: false
            if (!ok) {
                val reason = payload["reason"] as? String
                val retryAfter = (payload["retryAfter"] as? Number)?.toInt()
                val event = when (reason) {
                    "debounced" -> SnackbarEvent.Debounced(retryAfter)
                    "in_progress" -> SnackbarEvent.InProgress
                    else -> SnackbarEvent.GenericFailure(reason ?: "unknown")
                }
                _snackbarEvents.tryEmit(event)
            }
        } finally {
            _isRefreshing.value = false
            fetchMutex.unlock()
        }
    }

    /**
     * Server-side disconnect. Deletes the Secret Manager refresh token and
     * flips sync_status.linked=false; on success the local Prefs are cleared
     * so the device never carries the X credential again.
     */
    suspend fun disconnectX(): Result<Unit> = runCatching {
        callableService.disconnectX()
        authPref.clearAllTokens()
        Timber.d("Twitter tokens cleared after server-side disconnect")
    }

    private val pager = Pager(
        config = PagingConfig(
            pageSize = 20
        )
    ) {
        tweetDao.getTweets()
    }

    fun pagingTweetData() = pager.flow

    fun pagingTweetData(filter: FilterState): Flow<PagingData<TweetData>> {
        val type = filter.type.name
        val pagingSource = if (filter.selectedTags.isNotEmpty()) {
            { tweetDao.getTweetsByTagsTombstoneAware(filter.selectedTags.toList(), type) }
        } else {
            { tweetDao.getTweetsTombstoneAware(type) }
        }
        return Pager(
            config = PagingConfig(pageSize = 20),
            pagingSourceFactory = pagingSource,
        ).flow
    }

    /**
     * Reactive count of the feed the SAVED header reports. Built from the **same**
     * [FilterState] tags/no-tags branch and the **same** `:type` predicate as
     * [pagingTweetData], so the header tracks the visible list exactly. `distinctUntilChanged`
     * collapses no-op re-emissions from unrelated table churn during sync drains.
     */
    fun countFlow(filter: FilterState): Flow<Int> {
        val type = filter.type.name
        val source = if (filter.selectedTags.isNotEmpty()) {
            tweetDao.countByTagsTombstoneAware(filter.selectedTags.toList(), type)
        } else {
            tweetDao.countTombstoneAware(type)
        }
        return source.distinctUntilChanged()
    }

    suspend fun softDelete(id: String) {
        deletedBookmarkRepository.softDelete(id, BookmarkSource.Twitter)
    }

    suspend fun undoDelete(id: String) {
        deletedBookmarkRepository.undoDelete(id, BookmarkSource.Twitter)
    }

    /**
     * Swipe-right confirm: persist a Room tombstone (so the row disappears from
     * the paging Flow immediately) then stamp the server doc as deleted. The
     * Firestore write is best-effort offline — Room is the source of UI truth.
     */
    suspend fun confirmDeletePending(id: String) {
        deletedBookmarkRepository.softDelete(id, BookmarkSource.Twitter)
        runCatching { firestoreRepository.markDeleted(id) }
            .onFailure { Timber.w(it, "confirmDeletePending: Firestore mark failed for id=$id") }
    }

    /**
     * Swipe-left cancel: clear the local `pending_delete` flag first so the
     * card flips back to normal styling immediately, then sync to Firestore.
     */
    suspend fun cancelDeletePending(id: String) {
        tweetDao.updatePendingDelete(id, false)
        runCatching { firestoreRepository.cancelPendingDelete(id) }
            .onFailure { Timber.w(it, "cancelDeletePending: Firestore clear failed for id=$id") }
    }

    // Tag operations
    override suspend fun addTagToTweet(tweetId: String, tagName: String) {
        // Insert the tag if it doesn't exist
        tweetDao.insertTag(TagEntity(tagName))
        // Link the tag to the tweet
        tweetDao.insertTweetTag(TweetTagCrossRef(tweetId, tagName))
    }

    override suspend fun removeTagFromTweet(tweetId: String, tagName: String) {
        tweetDao.deleteTweetTag(tweetId, tagName)
    }

    override suspend fun getTagsForTweet(tweetId: String): List<String> {
        return tweetDao.getTagsForTweet(tweetId)
    }

    override suspend fun getTagsForItems(ids: List<String>): Map<String, List<String>> {
        if (ids.isEmpty()) return emptyMap()
        return tweetDao.getTagsForTweets(ids)
            .groupBy({ it.tweetId }, { it.tagName })
    }

    override suspend fun getAllTags(): List<String> {
        return tweetDao.getAllTags().map { it.name }
    }

    override suspend fun saveTags(tweetId: String, tags: List<String>) {
        // Get current tags
        val currentTags = getTagsForTweet(tweetId)

        // Remove tags that are no longer selected
        currentTags.forEach { tag ->
            if (tag !in tags) {
                removeTagFromTweet(tweetId, tag)
            }
        }

        // Add new tags
        tags.forEach { tag ->
            if (tag !in currentTags) {
                addTagToTweet(tweetId, tag)
            }
        }
    }

    /**
     * Clear all Twitter tokens to force re-authentication.
     */
    suspend fun logout() {
        authPref.clearAllTokens()
        Timber.d("Twitter tokens cleared")
    }
}
