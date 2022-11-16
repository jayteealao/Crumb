package com.github.jayteealao.crumbs.models.twitter

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.google.gson.annotations.SerializedName

data class TweetMedia(
    @SerializedName("media_key") val mediaKey: String,
    val type: String,
    val url: String?,
    @SerializedName("duration_ms") val durationMs: Int,
    val height: Int,
    val width: Int,
    @SerializedName("preview_image_url") val previewImageUrl: String?,
    @SerializedName("public_metrics") val publicMetrics: TweetPublicMetrics?,
    @SerializedName("alt_text") val altText: String?,
    val variants: List<Variant>?
)

data class Variant(
    @SerializedName("bit_rate") val bitRate: Int,
    @SerializedName("content_type") val contentType: String,
    val url: String
)

@Entity(
    tableName = "tweetMedia",
    primaryKeys = ["media_key"],
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
data class TweetMediaEntity(
    @ColumnInfo(name = "media_key") val mediaKey: String,
    val type: String,
    val url: String?,
    @ColumnInfo(name = "duration_ms") val durationMs: Int,
    val height: Int,
    val width: Int,
    @ColumnInfo(name = "preview_image_url") val previewImageUrl: String?,
    @ColumnInfo(name = "alt_text") val altText: String?,
    @ColumnInfo(name = "tweet_id") val tweetId: String? = null
)

fun TweetMedia.toTweetMediaEntity(tweetId: String) = TweetMediaEntity(
    mediaKey, type, url ?: variants?.get(0)?.url, durationMs, height, width, previewImageUrl, altText, tweetId
)

fun TweetMediaEntity.toTweetMedia() = TweetMedia(
    mediaKey, type, url, durationMs, height, width, previewImageUrl, tweetPublicMetrics(), altText, emptyList()
)
