package com.github.jayteealao.crumbs.models

import androidx.room.ColumnInfo
import com.google.gson.annotations.SerializedName

data class TweetAttachments(
    @SerializedName("poll_ids") val pollIds: List<String>?,
    @SerializedName("media_keys") val mediaKeys: List<String>?
)
data class PollIds(
    val tweetId: String,
    @PrimaryKey val id: String
)

data class MediaKeys(
    @ColumnInfo(name = "tweet_id") val tweetId: String,
    @PrimaryKey
    @ColumnInfo(name = "media_key")
    val mediaKey: String
)
