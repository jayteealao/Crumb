package com.github.jayteealao.twitter.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tweetReferencedTweets",
    // Backs the @Relation/@Junction lookup from a parent tweet to its referenced
    // (quoted) tweets (`WHERE tweet_id = :parentId`). Added in migration v17.
    indices = [Index("tweet_id")],
)
data class TweetReferencedTweets(
    @PrimaryKey(autoGenerate = true) val primaryId: Int = 0,
    val type: String,
    // The referenced (e.g. quoted) tweet's id.
    val id: String,
    // The parent (bookmarked) tweet that carries this reference — the junction
    // parent column for [TweetData.quotedTweets]. Intentionally FK-FREE: a reference
    // row may exist with NO resolvable quoted [TweetEntity] (a deleted/protected
    // quote), which is exactly the "unavailable" UI state. Added in migration v17;
    // the column is NOT NULL DEFAULT '' so legacy rows get a stable (orphan) value.
    @ColumnInfo(name = "tweet_id") val tweetId: String = "",
)

data class TweetReferencedTweetsFull(
    val referencedTweets: TweetReferencedTweets,
    val tweet: TweetEntity?
)

fun TweetReferencedTweets.toTweetReferencedTweetsFull(
    parentTweetId: String,
    includes: TweetIncludes,
) =
    TweetReferencedTweetsFull(
        // Stamp the parent id so the junction lookup resolves. The X-API DTO leaves
        // tweet_id unset (it is not in the wire payload), so copy it in here.
        referencedTweets = this.copy(tweetId = parentTweetId),
        includes.tweets?.firstOrNull { it.id == id }?.toTweetEntity(true)
    )
