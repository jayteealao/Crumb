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
    // Composite PK (tweet_id, media_key): the X v2 bookmarks API returns media once
    // per page, and a media_key can legitimately belong to more than one tweet on that
    // page (a quote/retweet surfaces the same asset on each owner). A sole `media_key`
    // PK could store only ONE tweet_id globally, so a shared asset collapsed onto an
    // arbitrary tweet (last-writer-wins) — the structural half of the wrong-media bug.
    // tweet_id leads the PK, but Room still warns MISSING_INDEX_ON_FOREIGN_KEY_CHILD
    // without the explicit @Index, so it is kept.
    primaryKeys = ["tweet_id", "media_key"],
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
    // Non-null: it is part of the composite PK. Every assembly/re-fetch path threads the
    // owning tweet id; the old nullable column (with a NULL-tweet_id rendering bug) was
    // repaired by migration v17→v18 and is wiped + re-pulled correctly by v18→v19.
    @ColumnInfo(name = "tweet_id") val tweetId: String,
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
