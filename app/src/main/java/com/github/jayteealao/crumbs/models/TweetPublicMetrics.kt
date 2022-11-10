package com.github.jayteealao.crumbs.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "tweetPublicMetrics",
    foreignKeys = [
        ForeignKey(
            entity = TweetEntity::class,
            parentColumns = ["id"],
            childColumns = ["tweet_id"]
        )
    ],
    indices = [
        Index(value = ["tweet_id"])
    ]
)
data class TweetPublicMetrics(
    @ColumnInfo(name = "retweet_count")
    @SerializedName("retweet_count")
    val retweetCount: Int?,
    @ColumnInfo(name = "reply_count")
    @SerializedName("reply_count")
    val replyCount: Int?,
    @ColumnInfo(name = "like_count")
    @SerializedName("like_count")
    val likeCount: Int?,
    @ColumnInfo(name = "quote_count")
    @SerializedName("quote_count")
    val quoteCount: Int?,
    @ColumnInfo(name = "view_count")
    @SerializedName("view_count")
    val viewCount: Int?,
    //    database only
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "tweet_id") val tweetId: String? = null
)

fun tweetPublicMetrics() = TweetPublicMetrics(0, 0, 0, 0, 0)
