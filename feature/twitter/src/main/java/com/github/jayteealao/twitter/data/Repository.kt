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
import com.github.jayteealao.twitter.models.tweetEntitiesToOrderLens
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Repository @Inject constructor(
    private val tweetDao: TweetDao,
    private val authPref: Prefs,
    private val firestoreRepository: FirestoreRepository,
    private val deletedBookmarkRepository: DeletedBookmarkRepository,
    private val functions: FirebaseFunctions,
    private val scope: CoroutineScope
) : TagRepository {
    private var latestBookmarkInDatabase: TweetEntity? = null
    private var orderOfLastBookmark: Int = 1000
    private val fetchMutex = Mutex()
    private val syncMutex = Mutex()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

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
                // Sync from Firestore on startup
                syncFromFirestore()
            } catch (e: Exception) {
                Timber.e(e, "Error in Repository init")
            }
        }
    }

    /**
     * Sync bookmarks from Firestore that are not in the local database.
     *
     * Single-flight: concurrent callers (cold-start init {}, pull-to-refresh,
     * any future re-call) are coalesced via [syncMutex]. If a sync is already
     * in flight, this returns immediately with a debug log. Callers that
     * already hold an outer lock (e.g. [refreshBookmarks] under [fetchMutex])
     * MUST call [syncFromFirestoreLocked] directly to avoid deadlocking
     * themselves on a redundant guard.
     */
    suspend fun syncFromFirestore() {
        if (!syncMutex.tryLock()) {
            Timber.d("syncFromFirestore: another sync in flight, skipping")
            return
        }
        try {
            syncFromFirestoreLocked()
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun syncFromFirestoreLocked() {
        try {
            Timber.d("Starting Firestore sync...")
            val localIds = tweetDao.getAllTweetIds().toSet()
            Timber.d("Local database has ${localIds.size} tweets")

            val firestoreTweets = firestoreRepository.fetchTweetsNotInLocal(localIds)
            Timber.d("Fetched ${firestoreTweets.size} tweets from Firestore")

            if (firestoreTweets.isNotEmpty()) {
                // Get the current max order to assign new orders
                var currentOrder = tweetDao.getMaxOrder() ?: 1000
                val tombstones = deletedBookmarkRepository.deletedIdsSnapshot(BookmarkSource.Twitter)

                firestoreTweets.forEach { tweetEntities ->
                    currentOrder++
                    val orderedEntities = tweetEntitiesToOrderLens.modify(tweetEntities) { currentOrder }
                    if (orderedEntities.tweetEntity.id !in tombstones) {
                        saveTweetEntities(orderedEntities, uploadToFirestore = false)
                    }
                }
                Timber.d("Synced ${firestoreTweets.size} tweets from Firestore to local database")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error syncing from Firestore")
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
     * Pull-to-refresh entry point. Invokes the `triggerPoll` callable against
     * the server; on `{ok: true}` re-runs the Firestore one-shot read to
     * surface any new docs. On `{ok: false}` emits a [SnackbarEvent] so the VM
     * can show user feedback.
     */
    suspend fun refreshBookmarks() {
        if (!fetchMutex.tryLock()) {
            Timber.d("refreshBookmarks: another refresh in flight, skipping")
            return
        }
        _isRefreshing.value = true
        try {
            val result = runCatching {
                functions.getHttpsCallable("triggerPoll").call(emptyMap<String, Any>()).await()
            }
            val payload = result.getOrNull()?.data as? Map<*, *>
            if (payload == null) {
                Timber.w("triggerPoll returned no payload; assuming failure")
                _snackbarEvents.tryEmit(
                    SnackbarEvent.GenericFailure(result.exceptionOrNull()?.message ?: "no_response")
                )
                return
            }

            val ok = payload["ok"] as? Boolean ?: false
            // Always pull from Firestore — even when the server says "debounced"
            // or "in_progress", the prior poll (e.g., the oauthCallback fan-out)
            // may have written docs the device hasn't synced locally yet. This
            // is the recovery path when Repository.init's startup sync raced
            // Firebase Auth restoration and silently failed.
            // Call the locked variant directly: we already hold fetchMutex, and
            // the public syncFromFirestore would skip on a parallel init {}
            // sync — exactly the case where we most need the pull to land.
            syncFromFirestoreLocked()
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
        val response = functions
            .getHttpsCallable("disconnectX")
            .call(emptyMap<String, Any>())
            .await()
        val payload = response.data as? Map<*, *>
        val ok = payload?.get("ok") as? Boolean ?: false
        if (!ok) {
            throw IllegalStateException(payload?.get("reason") as? String ?: "disconnect_failed")
        }
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
        val pagingSource = if (filter.selectedTags.isNotEmpty()) {
            { tweetDao.getTweetsByTagsTombstoneAware(filter.selectedTags.toList()) }
        } else {
            { tweetDao.getTweetsTombstoneAware() }
        }
        return Pager(
            config = PagingConfig(pageSize = 20),
            pagingSourceFactory = pagingSource,
        ).flow
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
