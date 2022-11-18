package com.github.jayteealao.crumbs.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.github.jayteealao.crumbs.models.twitter.TweetEntities
import com.github.jayteealao.crumbs.models.twitter.TweetEntity
import com.github.jayteealao.crumbs.models.twitter.tweetEntitiesToOrderLens
import com.github.jayteealao.crumbs.services.AuthPref
import com.github.jayteealao.crumbs.services.twitter.TwitterApiClient
import com.github.jayteealao.crumbs.services.twitter.TwitterAuthClient
import com.github.jayteealao.crumbs.utils.produceTweetResponseEntities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Repository @Inject constructor(
    private val tweetDao: TweetDao,
    private val authPref: AuthPref,
    private val twitterApiClient: TwitterApiClient,
    private val twitterAuthClient: TwitterAuthClient,
    private val scope: CoroutineScope
) {
    private var latestBookmarkInDatabase: TweetEntity? = null
    private var orderOfLastBookmark: Int = 1000
    private var needsRefresh = false
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
        var id = latestBookmarkInDatabase?.id
        var saveThreads = true
        scope.launch(Dispatchers.IO) {
            latestBookmarkInDatabase = tweetDao.getLatestBookmark()
            Timber.tag("latest bookmark in database: $latestBookmarkInDatabase")
            if (latestBookmarkInDatabase != null) {
                orderOfLastBookmark = latestBookmarkInDatabase!!.order
                id = latestBookmarkInDatabase!!.id
            }
            combine(authPref.accessCode, authPref.userId, authPref.refreshCode) {
                    accessCode, userId, refreshToken ->
                if (refreshToken.isNotBlank() && userId.isNotBlank()) {
                    Timber.d("building database: latestId: $id")
                    val tweetEntitiesChannel =
                        produceTweetResponseEntities(
                            refreshToken,
                            latestIdInDb = id,
                            onError = { twitterAuthClient.refreshAccessToken(refreshToken) }
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
                            launch(Dispatchers.IO) {
//                                Timber.d("saving entity ${it.tweetEntity.id}")
                                saveTweetEntities(tweetEntitiesToOrderLens.modify(it) { order })
                                if (saveThreads) {
                                    saveTweetThreads(it.tweetEntity.authorId, it.tweetEntity.conversationId)
                                    saveThreads = id != it.tweetEntity.id
                                } // during updates prevent savetweetthread for running for the 100 tweets in a

                            }
                            orderStart--
                        }
                    }
                }
            }.collect {}
        }
    }

    fun saveTweetThreads(tweetAuthorId: String, conversationId: String) {
        scope.launch {

            combine(authPref.accessCode, authPref.userId, authPref.refreshCode) {
                    accessCode, userId, refreshToken ->
                if (refreshToken.isNotBlank() && userId.isNotBlank()) {
                    val tweetEntitiesChannel =
                        produceTweetResponseEntities(
                            refreshToken,
                            latestIdInDb = "",
                            onError = {  }
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
            }.collect {}
        }
    }

    fun saveTweetThreadsAppOnly(tweetAuthorId: String, conversationId: String) {
        scope.launch {

            combine(authPref.accessCodeAppOnly, authPref.userId, authPref.refreshCode) {
                    accessCode, userId, refreshToken ->
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
            }.collect {}
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
