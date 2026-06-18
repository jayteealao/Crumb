package com.github.jayteealao.crumbs.models

/**
 * One playable video stream for a bookmark, decoupled from any Twitter/Media3 type so
 * the shared [Bookmark] model and `core/designsystem` can carry and select streams
 * without depending on `feature/twitter`.
 *
 * [contentType] is the MIME type as reported by the source (e.g.
 * `application/x-mpegURL` for HLS, `application/dash+xml` for DASH, `video/mp4` for a
 * progressive stream). [bitRate] is bits-per-second for progressive variants and `0`
 * for adaptive manifests (which carry their own ladder), used only to rank progressive
 * fallbacks. The player picks HLS first, then DASH, then the highest-bitrate MP4.
 */
data class VideoVariant(
    val contentType: String,
    val url: String,
    val bitRate: Int = 0,
)
