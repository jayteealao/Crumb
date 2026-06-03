package com.github.jayteealao.twitter.models

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
    @ColumnInfo(name = "tweet_id") val tweetId: String? = null,
    // HLS / DASH / progressive stream variants for video & animated_gif rows,
    // persisted as JSON via [MediaConverters] (column added in migration v14→v15).
    // Null for photo rows and for legacy rows synced before the column existed —
    // those repair via the lazy on-view re-fetch + the widened backfill sweep.
    @ColumnInfo(name = "video_variants") val videoVariants: List<Variant>? = null,
)

fun TweetMedia.toTweetMediaEntity(tweetId: String) = TweetMediaEntity(
    mediaKey = mediaKey,
    type = type,
    url = url ?: variants?.firstOrNull()?.url,
    durationMs = durationMs,
    height = height,
    width = width,
    previewImageUrl = previewImageUrl,
    altText = altText,
    tweetId = tweetId,
    videoVariants = variants?.takeIf { it.isNotEmpty() },
)

fun TweetMediaEntity.toTweetMedia() = TweetMedia(
    mediaKey, type, url, durationMs, height, width, previewImageUrl, tweetPublicMetrics(), altText, videoVariants
)
