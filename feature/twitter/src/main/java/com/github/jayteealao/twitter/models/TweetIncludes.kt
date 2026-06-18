package com.github.jayteealao.twitter.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
* TweetIncludes is a dataclass that holds the results if expansions in the twitter v2 api request
* */
data class TweetIncludes(
    val users: List<TwitterUser?> = emptyList(),
    val media: List<TweetMedia>?,
    val tweets: List<Tweet>?
)

fun tweetIncludes() = TweetIncludes(
    users = emptyList(),
    media = emptyList(),
    tweets = emptyList()
)

@Entity(
    tableName = "tweetIncludes",
    // The FK to tweetMedia(media_key) was dropped: tweetMedia's PK is now the composite
    // (tweet_id, media_key), so media_key alone is no longer a unique/PK parent column and
    // SQLite cannot reference it. The media_key column + its @Index are kept (harmless; the
    // Firestore path writes this table empty anyway), only the foreign-key constraint is gone.
    foreignKeys = [
        ForeignKey(
            entity = TwitterUserEntity::class,
            parentColumns = ["id"],
            childColumns = ["twitter_user"]
        ),
        ForeignKey(
            entity = TweetEntity::class,
            parentColumns = ["id"],
            childColumns = ["referenced_tweet_id"]
        )
    ],
    indices = [
        Index(value = ["twitter_user"]),
        Index(value = ["referenced_tweet_id"]),
        Index(value = ["media_key"])
    ]
)
data class TweetIncludesEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "tweet_id") val tweetId: String,
    @ColumnInfo(name = "twitter_user") val twitterUser: String? = null,
    @ColumnInfo(name = "referenced_tweet_id") val referencedTweetId: String? = null,
    @ColumnInfo(name = "media_key") val mediaKey: String? = null
)

fun TweetIncludes.toTweetIncludesEntityForTweet(
    tweet: Tweet
): List<TweetIncludesEntity> {
    val mentions = tweet.entities?.mentions
        ?.filter { !it.id.isNullOrBlank() }
        ?.map { it.id!! } ?: emptyList()
    val refTweets = tweet.referencedTweets?.map { it.id } ?: emptyList()

    return emptySequence<TweetIncludesEntity>()
        .plus(
            users.filter {
                it?.id == tweet.authorId ||
                    mentions.contains(it?.id) || it?.id == tweet.inReplyToUserId
            }
                .map { TweetIncludesEntity(tweetId = tweet.id, twitterUser = it?.id) }
        )
        .plus(
            media?.filter { tweet.attachments?.mediaKeys?.contains(it.mediaKey) == true }
                ?.map { TweetIncludesEntity(tweetId = tweet.id, mediaKey = it.mediaKey) } ?: emptyList()
        )
        .plus(
            tweets?.filter { refTweets.contains(it.id) }
                ?.map { TweetIncludesEntity(0, tweet.id, referencedTweetId = it.id) } ?: emptyList()
        )
        .toList()
}
