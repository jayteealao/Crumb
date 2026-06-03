package com.github.jayteealao.twitter.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.github.jayteealao.crumbs.models.VideoVariant

/**
 * Pure selection of the single best playable stream from a tweet's [VideoVariant] list,
 * and its conversion to a Media3 [MediaItem]. No Android framework state — unit-testable
 * on the JVM ([com.github.jayteealao.twitter.player.VariantSelectionTest]).
 *
 * Order of preference:
 *  1. **HLS** (`application/x-mpegURL`) — adaptive; the player picks the rendition.
 *  2. **DASH** (`application/dash+xml`) — adaptive fallback when no HLS.
 *  3. **Highest-bitrate progressive MP4** (`video/mp4`) — fixed rendition fallback.
 *  4. Any remaining variant with a non-blank URL (defensive).
 *
 * Twitter CDN variant URLs carry NO file extension, so [DefaultMediaSourceFactory]'s
 * extension-based auto-detection silently falls back to progressive and adaptive
 * streams fail. We therefore set [MediaItem.Builder.setMimeType] explicitly so the
 * ServiceLoader-registered HLS/DASH factories are selected — this is mandatory, not
 * cosmetic. The HLS/DASH artifacts must be on the runtime classpath
 * (`feature/twitter/build.gradle`).
 */
object VariantSelection {

    private const val HLS = "application/x-mpegurl"
    private const val DASH = "application/dash+xml"
    private const val MP4 = "video/mp4"

    /** The variant the player should load, applying the preference order above, or null when none is playable. */
    fun select(variants: List<VideoVariant>): VideoVariant? {
        val playable = variants.filter { it.url.isNotBlank() }
        if (playable.isEmpty()) return null
        return playable.firstOrNull { it.contentType.equals(HLS, ignoreCase = true) }
            ?: playable.firstOrNull { it.contentType.equals(DASH, ignoreCase = true) }
            ?: playable.filter { it.contentType.equals(MP4, ignoreCase = true) }.maxByOrNull { it.bitRate }
            ?: playable.maxByOrNull { it.bitRate }
    }

    /** Best single playable URL for the back-compat [com.github.jayteealao.crumbs.models.Bookmark.videoUrl] field. */
    fun bestUrl(variants: List<VideoVariant>): String? = select(variants)?.url

    /**
     * Build a [MediaItem] for the selected variant with an explicit MIME type so HLS / DASH
     * are recognised on extensionless Twitter CDN URLs. Returns null when no variant is playable
     * (caller degrades to the poster / text-only card).
     */
    fun toMediaItem(variants: List<VideoVariant>): MediaItem? {
        val variant = select(variants) ?: return null
        val mimeType = when {
            variant.contentType.equals(HLS, ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
            variant.contentType.equals(DASH, ignoreCase = true) -> MimeTypes.APPLICATION_MPD
            else -> MimeTypes.VIDEO_MP4
        }
        return MediaItem.Builder()
            .setUri(variant.url)
            .setMimeType(mimeType)
            .build()
    }
}
