package com.github.jayteealao.crumbs.models.twitter

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

data class TweetAttachments(
    @SerializedName("poll_ids") val pollIds: List<String>?,
    @SerializedName("media_keys") val mediaKeys: List<String>?
)

@Entity(
    tableName = "pollIds",
    foreignKeys = [
        ForeignKey(
            entity = TweetEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("tweetId")

        )
    ]
)
data class PollIds(
    val tweetId: String,
    @PrimaryKey val id: String
)

@Entity(
    tableName = "mediaKeys",
    foreignKeys = [
        ForeignKey(
            entity = TweetEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("tweet_id")

        )
    ]
)
data class MediaKeys(
    @ColumnInfo(name = "tweet_id") val tweetId: String,
    @PrimaryKey
    @ColumnInfo(name = "media_key")
    val mediaKey: String
)
