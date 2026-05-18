package com.github.jayteealao.twitter.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.data.DeletedBookmarkRepository
import com.github.jayteealao.crumbs.data.FilterState
import com.github.jayteealao.crumbs.data.SyncErrorBus
import com.github.jayteealao.crumbs.data.SyncErrorEvent
import com.github.jayteealao.crumbs.data.TagRepository
import com.github.jayteealao.crumbs.utils.produceTweetResponseEntities
import com.github.jayteealao.twitter.data.firestore.FirestoreRepository
import com.github.jayteealao.twitter.models.TagEntity
import com.github.jayteealao.twitter.models.TweetData
import com.github.jayteealao.twitter.models.TweetEntities
import com.github.jayteealao.twitter.models.TweetEntity
import com.github.jayteealao.twitter.models.TweetTagCrossRef
import com.github.jayteealao.twitter.models.tweetEntitiesToOrderLens
import com.github.jayteealao.twitter.services.TwitterApiClient
import com.github.jayteealao.twitter.services.TwitterAuthClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Repository @Inject constructor(
    private val tweetDao: TweetDao,
    private val authPref: Prefs,
    private val twitterApiClient: TwitterApiClient,
    private val twitterAuthClient: TwitterAuthClient,
    private val firestoreRepository: FirestoreRepository,
    private val deletedBookmarkRepository: DeletedBookmarkRepository,
    private val syncErrorBus: SyncErrorBus,
    private val scope: CoroutineScope
) : TagRepository {
    private var latestBookmarkInDatabase: TweetEntity? = null
    private var orderOfLastBookmark: Int = 1000
    private var needsRefresh = false
    private val fetchMutex = Mutex()
    // Guards concurrent token refresh attempts; pairs with [refreshTokenSingleFlight]
    // so a 401 storm from parallel paginations does not spawn duplicate refresh calls.
    private val refreshMutex = Mutex()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
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
     * Sync bookmarks from Firestore that are not in the local database
     */
    suspend fun syncFromFirestore() {
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

    fun buildDatabase() {
        scope.launch(Dispatchers.IO) {
            refreshBookmarksInternal()
        }
    }

    suspend fun refreshBookmarks() {
        refreshBookmarksInternal()
    }

    private suspend fun refreshBookmarksInternal() {
        // Single-flight: tryLock fails fast if another fetch holds the mutex.
        // Mutex + try/finally ensures the lock is released even on cancellation,
        // unlike the prior split-lock pattern that could orphan an `isFetching=true`.
        if (!fetchMutex.tryLock()) {
            Timber.d("buildDatabase: Already fetching, skipping")
            return
        }
        _isRefreshing.value = true

        try {
            latestBookmarkInDatabase = tweetDao.getLatestBookmark()
            Timber.d("latest bookmark in database: $latestBookmarkInDatabase")
            if (latestBookmarkInDatabase != null) {
                orderOfLastBookmark = latestBookmarkInDatabase!!.order
            }

            // Resolve userId + refreshToken once; accessCode must be re-read
            // on every API call so the refresh-first auth recovery actually
            // takes effect. Capturing accessCode once meant a successful
            // silent refresh still left the loop using the stale token,
            // producing an infinite 401 retry with no UI feedback.
            val (userId, refreshToken) = combine(
                authPref.userId,
                authPref.refreshCode
            ) { user, refresh -> user to refresh }
                .first()

            if (refreshToken.isNotBlank() && userId.isNotBlank()) {
                Timber.d("building database: fetching new bookmarks incrementally")
                val tombstones = deletedBookmarkRepository.deletedIdsSnapshot(BookmarkSource.Twitter)
                val tweetEntitiesChannel =
                    scope.produceTweetResponseEntities(
                        refreshToken,
                        latestIdInDb = latestBookmarkInDatabase?.id,
                        onError = {
                            // Refresh-first: silent recovery beats an alarming banner if
                            // the token is merely stale. Only surface to the user when the
                            // refresh itself fails (true loss of session).
                            val recovered = refreshTokenSingleFlight(refreshToken)
                            if (!recovered) {
                                syncErrorBus.emit(SyncErrorEvent.TwitterAuth401())
                            }
                        }
                    ) {
                        // Re-read accessCode on every call so a refresh that
                        // landed via refreshTokenSingleFlight is actually used.
                        val currentAccessCode = authPref.accessCode.first()
                        twitterApiClient.getBookmarks(
                            "Bearer $currentAccessCode",
                            userId,
                            it
                        )
                    }
                var orderStart = orderOfLastBookmark + BUFFER
                tweetEntitiesChannel.consumeEach {
                    it.data.forEach {
                        val order = orderStart
                        if (it.tweetEntity.id !in tombstones) {
                            scope.launch(Dispatchers.IO) {
                                saveTweetEntities(tweetEntitiesToOrderLens.modify(it) { order })
                            }
                        }
                        orderStart--
                    }
                }
            }
        } finally {
            _isRefreshing.value = false
            fetchMutex.unlock()
        }
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
     * Single-flight access-token refresh. Returns true if a fresh access token
     * has been persisted; false on hard failure.
     *
     * Uses `withLock` (not tryLock) so a concurrent 401 from a sibling
     * pagination call waits for the in-flight refresh to finish and observes
     * the actual outcome, rather than skipping and falsely claiming success
     * (which would suppress the banner while the loop kept 401-ing).
     * twitterAuthClient.refreshAccessToken does NOT persist by itself, so we
     * write back via authPref.setAccessAndRefreshToken before returning.
     */
    private suspend fun refreshTokenSingleFlight(currentRefreshToken: String): Boolean {
        return refreshMutex.withLock {
            // Re-read once inside the lock; if a previous holder already
            // refreshed, the persisted refreshToken may have rotated.
            val tokenToUse = authPref.refreshCode.first().ifBlank { currentRefreshToken }
            try {
                val tokenResponse = twitterAuthClient.refreshAccessToken(tokenToUse)
                val access = tokenResponse?.accessToken
                val refresh = tokenResponse?.refreshToken
                if (!access.isNullOrBlank() && !refresh.isNullOrBlank()) {
                    authPref.setAccessAndRefreshToken(access, refresh)
                    Timber.d("refreshTokenSingleFlight: token refreshed and persisted")
                    true
                } else {
                    Timber.w("refreshTokenSingleFlight: refresh returned null/blank tokens")
                    false
                }
            } catch (e: Exception) {
                Timber.e(e, "refreshTokenSingleFlight: exception during refresh")
                false
            }
        }
    }

    /**
     * Clear all Twitter tokens to force re-authentication
     */
    suspend fun logout() {
        authPref.clearAllTokens()
        Timber.d("Twitter tokens cleared")
    }
}
