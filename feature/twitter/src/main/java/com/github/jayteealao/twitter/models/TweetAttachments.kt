package com.github.jayteealao.twitter.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
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
    ],
    // Index the FK column so deleting/updating a parent tweet does not
    // trigger a full pollIds scan (KSP warns about this otherwise).
    indices = [Index("tweetId")]
)
data class PollIds(
    val tweetId: String,
    @PrimaryKey val id: String
)

@Entity(
    tableName = "mediaKeys",
    // Composite PK (tweet_id, media_key), matching tweetMedia: a media_key shared across
    // tweets on one fetch page must map to each owner, not collapse onto one arbitrarily.
    primaryKeys = ["tweet_id", "media_key"],
    foreignKeys = [
        ForeignKey(
            entity = TweetEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("tweet_id")

        )
    ],
    indices = [Index("tweet_id")]
)
data class MediaKeys(
    @ColumnInfo(name = "tweet_id") val tweetId: String,
    @ColumnInfo(name = "media_key")
    val mediaKey: String
)
