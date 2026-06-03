package com.github.jayteealao.twitter.player

import androidx.media3.common.MimeTypes
import com.github.jayteealao.crumbs.models.VideoVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Locks the stream-selection order (HLS → DASH → highest-bitrate MP4) and the explicit
 * MIME tagging that lets Media3 recognise extensionless Twitter CDN URLs. Robolectric so
 * `MediaItem.Builder().setUri(String)` (which parses an `android.net.Uri`) resolves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VariantSelectionTest {

    private val hls = VideoVariant("application/x-mpegURL", "https://v/master.m3u8", 0)
    private val dash = VideoVariant("application/dash+xml", "https://v/manifest.mpd", 0)
    private val mp4Low = VideoVariant("video/mp4", "https://v/480.mp4", 832_000)
    private val mp4High = VideoVariant("video/mp4", "https://v/720.mp4", 2_176_000)

    @Test
    fun prefersHlsOverEverything() {
        assertEquals(hls, VariantSelection.select(listOf(mp4High, dash, hls, mp4Low)))
    }

    @Test
    fun prefersDashWhenNoHls() {
        assertEquals(dash, VariantSelection.select(listOf(mp4High, dash, mp4Low)))
    }

    @Test
    fun picksHighestBitrateMp4WhenNoAdaptive() {
        assertEquals(mp4High, VariantSelection.select(listOf(mp4Low, mp4High)))
    }

    @Test
    fun returnsNullWhenEmpty() {
        assertNull(VariantSelection.select(emptyList()))
    }

    @Test
    fun skipsBlankUrls() {
        val blank = VideoVariant("video/mp4", "", 9_999_999)
        assertEquals(mp4High, VariantSelection.select(listOf(blank, mp4High)))
    }

    @Test
    fun toMediaItemTagsHlsMimeTypeForExtensionlessUrl() {
        val item = VariantSelection.toMediaItem(listOf(hls, mp4High))
        assertEquals(MimeTypes.APPLICATION_M3U8, item?.localConfiguration?.mimeType)
        assertEquals("https://v/master.m3u8", item?.localConfiguration?.uri.toString())
    }

    @Test
    fun toMediaItemTagsMp4MimeTypeForProgressiveOnly() {
        val item = VariantSelection.toMediaItem(listOf(mp4Low, mp4High))
        assertEquals(MimeTypes.VIDEO_MP4, item?.localConfiguration?.mimeType)
        assertEquals("https://v/720.mp4", item?.localConfiguration?.uri.toString())
    }

    @Test
    fun toMediaItemNullWhenNoPlayableVariant() {
        assertNull(VariantSelection.toMediaItem(emptyList()))
    }
}
