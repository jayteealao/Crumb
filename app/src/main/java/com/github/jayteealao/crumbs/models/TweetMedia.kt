package com.github.jayteealao.crumbs.models
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

