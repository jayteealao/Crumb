package com.github.jayteealao.twitter.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.github.jayteealao.crumbs.data.BookmarkSource
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
                val tombstones = deletedBookmarkRepository.deletedIdsSnapshot()

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

            // Get current values once instead of continuously collecting
            val (accessCode, userId, refreshToken) = combine(
                authPref.accessCode,
                authPref.userId,
                authPref.refreshCode
            ) { access, user, refresh -> Triple(access, user, refresh) }
                .first()

            if (refreshToken.isNotBlank() && userId.isNotBlank()) {
                Timber.d("building database: fetching new bookmarks incrementally")
                val tombstones = deletedBookmarkRepository.deletedIdsSnapshot()
                val tweetEntitiesChannel =
                    scope.produceTweetResponseEntities(
                        refreshToken,
                        latestIdInDb = latestBookmarkInDatabase?.id,
                        onError = {
                            syncErrorBus.emit(SyncErrorEvent.TwitterAuth401())
                            twitterAuthClient.refreshAccessToken(refreshToken)
                        }
                    ) {
                        twitterApiClient.getBookmarks(
                            "Bearer $accessCode",
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
        deletedBookmarkRepository.softDelete(id, BookmarkSource.TWITTER)
    }

    suspend fun undoDelete(id: String) {
        deletedBookmarkRepository.undoDelete(id)
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
     * Clear all Twitter tokens to force re-authentication
     */
    suspend fun logout() {
        authPref.clearAllTokens()
        Timber.d("Twitter tokens cleared")
    }
}
