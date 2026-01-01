package com.github.jayteealao.twitter.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.github.jayteealao.crumbs.utils.produceTweetResponseEntities
import com.github.jayteealao.twitter.models.TweetEntities
import com.github.jayteealao.twitter.models.TweetEntity
import com.github.jayteealao.twitter.models.tweetEntitiesToOrderLens
import com.github.jayteealao.twitter.services.TwitterApiClient
import com.github.jayteealao.twitter.services.TwitterAuthClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    private val scope: CoroutineScope
) {
    private var latestBookmarkInDatabase: TweetEntity? = null
    private var orderOfLastBookmark: Int = 1000
    private var needsRefresh = false
    private val fetchMutex = Mutex()
    private var isFetching = false
    companion object {
        const val BUFFER = 250
    }

    init {
        scope.launch(Dispatchers.IO) {
            latestBookmarkInDatabase = tweetDao.getLatestBookmark()
            Timber.tag("latest bookmark in database: $latestBookmarkInDatabase")
            if (latestBookmarkInDatabase != null) {
                orderOfLastBookmark = latestBookmarkInDatabase!!.order
            }
        }
    }

    fun saveTweetEntities(tweetEntities: TweetEntities) = tweetDao.insertTweetEntities(
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
        tweetEntities.pollIds
    )

    fun buildDatabase() {
        scope.launch(Dispatchers.IO) {
            // Prevent concurrent fetches
            fetchMutex.withLock {
                if (isFetching) {
                    Timber.d("buildDatabase: Already fetching, skipping")
                    return@launch
                }
                isFetching = true
            }

            try {
                var saveThreads = true

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
                    Timber.d("building database: fetching all bookmarks (up to 800)")
                    val tweetEntitiesChannel =
                        produceTweetResponseEntities(
                            refreshToken,
                            latestIdInDb = null, // Fetch all bookmarks, don't stop early
                            onError = { twitterAuthClient.refreshAccessToken(refreshToken) }
                        ) {
                            twitterApiClient.getBookmarks(
                                "Bearer $accessCode",
                                userId,
                                it
                            )
                        }
                    var orderStart = orderOfLastBookmark + BUFFER
                    val existingLatestId = latestBookmarkInDatabase?.id
                    tweetEntitiesChannel.consumeEach {
                        it.data.forEach {
                            val order = orderStart
                            launch(Dispatchers.IO) {
                                saveTweetEntities(tweetEntitiesToOrderLens.modify(it) { order })
                                // Only save threads for new bookmarks (not already in database)
                                if (saveThreads) {
                                    saveTweetThreads(it.tweetEntity.authorId, it.tweetEntity.conversationId)
                                    // Stop saving threads once we reach existing bookmarks
                                    if (existingLatestId == it.tweetEntity.id) {
                                        saveThreads = false
                                    }
                                }
                            }
                            orderStart--
                        }
                    }
                }
            } finally {
                fetchMutex.withLock {
                    isFetching = false
                }
            }
        }
    }

    fun saveTweetThreads(tweetAuthorId: String, conversationId: String) {
        scope.launch(Dispatchers.IO) {
            val (accessCode, userId, refreshToken) = combine(
                authPref.accessCode,
                authPref.userId,
                authPref.refreshCode
            ) { access, user, refresh -> Triple(access, user, refresh) }
                .first()

            if (refreshToken.isNotBlank() && userId.isNotBlank()) {
                val tweetEntitiesChannel =
                    produceTweetResponseEntities(
                        refreshToken,
                        latestIdInDb = "",
                        onError = { }
                    ) {
                        twitterApiClient.getTweetThread(
                            "Bearer $accessCode",
                            tweetAuthorId,
                            conversationId,
                            it
                        )
                    }
                tweetEntitiesChannel.consumeEach {
                    it.data.forEach {
                        launch(Dispatchers.IO) {
                            Timber.d("saving entity ${it.tweetEntity}")
                            saveTweetEntities(it)
                        }
                    }
                }
            }
        }
    }

    fun saveTweetThreadsAppOnly(tweetAuthorId: String, conversationId: String) {
        scope.launch(Dispatchers.IO) {
            val (accessCode, userId, refreshToken) = combine(
                authPref.appAccessCode,
                authPref.userId,
                authPref.refreshCode
            ) { access, user, refresh -> Triple(access, user, refresh) }
                .first()

            if (refreshToken.isNotBlank() && userId.isNotBlank()) {
                val tweetEntitiesChannel =
                    produceTweetResponseEntities(
                        refreshToken,
                        latestIdInDb = "",
                        onError = { twitterAuthClient.refreshAccessToken(refreshToken) }
                    ) {
                        twitterApiClient.getTweetThread2(
                            "Bearer $accessCode",
                            tweetAuthorId,
                            conversationId,
                            it
                        )
                    }
                tweetEntitiesChannel.consumeEach {
                    it.data.forEach {
                        launch(Dispatchers.IO) {
                            Timber.d("saving entity ${it.tweetEntity}")
                            saveTweetEntities(it)
                        }
                    }
                }
            }
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

    fun getThreadIds(): List<IdForThread> = tweetDao.getLatestThreadId()
}
