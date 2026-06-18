package com.github.jayteealao.twitter.data

import com.github.jayteealao.twitter.data.firestore.FirestoreRepository
import com.github.jayteealao.twitter.models.TweetEntities
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [TwitterSyncFacade]. Delegates to the existing
 * [TweetDao] and [FirestoreRepository] public methods without modifying either.
 */
@Singleton
class TwitterSyncFacadeImpl @Inject constructor(
    private val tweetDao: TweetDao,
    private val firestoreRepository: FirestoreRepository,
) : TwitterSyncFacade {

    override suspend fun getAllTweetIds(): List<String> =
        tweetDao.getAllTweetIds()

    override suspend fun getMaxOrder(): Int? =
        tweetDao.getMaxOrder()

    override fun insertTweetEntitiesBatch(batch: List<TweetEntities>) =
        tweetDao.insertTweetEntitiesBatch(batch)

    override fun fetchMissingTweetsStream(
        localIds: Set<String>,
        deletedIds: Set<String>,
    ): Flow<List<TweetEntities>> =
        firestoreRepository.fetchTweetsNotInLocalStream(localIds, deletedIds)

    override suspend fun getTweetsWithoutMedia(afterId: String, limit: Int): List<String> =
        tweetDao.getTweetsWithoutMedia(afterId, limit)

    override suspend fun getVideoTweetsWithoutVariants(afterId: String, limit: Int): List<String> =
        tweetDao.getVideoTweetsWithoutVariants(afterId, limit)

    override suspend fun getExternalLinkTweetsWithoutPreview(afterId: String, limit: Int): List<String> =
        tweetDao.getExternalLinkTweetsWithoutPreview(afterId, limit)

    override suspend fun getQuoteTweetsWithoutBody(afterId: String, limit: Int): List<String> =
        tweetDao.getQuoteTweetsWithoutBody(afterId, limit)
}
