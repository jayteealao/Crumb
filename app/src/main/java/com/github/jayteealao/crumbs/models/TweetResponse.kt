package com.github.jayteealao.crumbs.models

import arrow.optics.Lens
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.ApiSuccessModelMapper

data class TweetResponse(
    val data: List<Tweet>,
    val meta: TweetMeta,
    val includes: TweetIncludes?
)

data class TweetResponseEntities(
    val data: List<TweetEntities>,
    val meta: TweetMeta
)

data class TweetEntities(
    val tweetEntity: TweetEntity,
    val twitterUserEntity: List<TwitterUserEntity>,
    val tweetPublicMetrics: TweetPublicMetrics,
    val tweetMediaEntity: List<TweetMediaEntity>,
    val tweetIncludesEntity: List<TweetIncludesEntity>,
    val tweetReferencedTweets: List<TweetReferencedTweetsFull>,
    val tweetContextAnnotationEntity: List<TweetContextAnnotationEntity>,
    val tweetTextEntity: List<TweetTextEntityAnnotation>,
    val mediaKeys: List<MediaKeys> = emptyList(),
    val pollIds: PollIds? = null
)

fun mapTweetDataToTweetEntity(tweets: List<Tweet>, includes: TweetIncludes, page: Int = 0): List<TweetEntities> {
    return tweets.mapIndexed { _, tweet ->
        val mediaKeys = tweet.attachments?.mediaKeys ?: emptyList()
        val pollId = tweet.attachments?.pollIds?.singleOrNull()
        TweetEntities(
            tweetEntity = tweet.toTweetEntity(),
            tweetPublicMetrics = tweet.publicMetrics.copy(tweetId = tweet.id),
            tweetMediaEntity = includes.media?.filter { mediaKeys.contains(it.mediaKey) }
                ?.map { it.toTweetMediaEntity(tweet.id) } ?: emptyList(),
            tweetIncludesEntity = includes.toTweetIncludesEntityForTweet(tweet),
            twitterUserEntity = includes.extractUsersInTweet(tweet),
            tweetReferencedTweets = tweet.referencedTweets
                ?.map { it.toTweetReferencedTweetsFull(includes) } ?: emptyList(),
            tweetContextAnnotationEntity = tweet.contextAnnotation
                ?.map { it.toTweetContextAnnotationEntity(tweet.id) } ?: emptyList(),
            tweetTextEntity = tweet.entities?.toTweetTextEntityAnnotation(tweet.id) ?: emptyList(),
            mediaKeys = mediaKeys.map { MediaKeys(tweet.id, it) },
            pollIds = if (pollId != null) PollIds(tweet.id, pollId) else PollIds(tweet.id, "none${tweet.id}") // TODO: fix
        )
    }
}

object SuccessMapper : ApiSuccessModelMapper<TweetResponse, TweetResponseEntities> {

    override fun map(apiErrorResponse: ApiResponse.Success<TweetResponse>): TweetResponseEntities {
        return TweetResponseEntities(
            data = mapTweetDataToTweetEntity(apiErrorResponse.data.data ?: emptyList(), apiErrorResponse.data.includes ?: tweetIncludes()),
            meta = apiErrorResponse.data.meta
        )
    }
}

val tweetEntitiesTweetEntityLens: Lens<TweetEntities, TweetEntity> = Lens(
    get = { it.tweetEntity },
    set = { tweetEntities, tweetEntity ->
        tweetEntities.copy(tweetEntity = tweetEntity)
    }
)

val tweetEntityOrderLens: Lens<TweetEntity, Int> = Lens(
    get = { it.order },
    set = { tweet, order -> tweet.copy(order = order) }
)

val tweetEntitiesToOrderLens: Lens<TweetEntities, Int> = tweetEntitiesTweetEntityLens compose tweetEntityOrderLens
