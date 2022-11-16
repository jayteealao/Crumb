package com.github.jayteealao.crumbs.models.twitter

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "tweetReferencedTweets"
)
data class TweetReferencedTweets(
    @PrimaryKey(autoGenerate = true) val primaryId: Int = 0,
    val type: String,
    val id: String
)

data class TweetReferencedTweetsFull(
    val referencedTweets: TweetReferencedTweets,
    val tweet: TweetEntity?
)

fun TweetReferencedTweets.toTweetReferencedTweetsFull(includes: TweetIncludes) =
    TweetReferencedTweetsFull(
        referencedTweets = this,
        includes.tweets?.firstOrNull { it.id == id }?.toTweetEntity(true)
    )
