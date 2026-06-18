package com.github.jayteealao.crumbs.models

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers [toRelativeTime] bucket boundaries and, critically, the [Bookmark.UNKNOWN_TIME]
 * sentinel rendering as the `_` marker (never a fabricated "now"). Inputs are anchored to the
 * current time mid-bucket so the few-millisecond delta between the test's clock read and the
 * function's own read cannot cross a boundary.
 */
class ToRelativeTimeTest {

    private fun ago(deltaMs: Long): Long = System.currentTimeMillis() - deltaMs

    @Test
    fun unknownTimeSentinel_rendersUnderscoreMarker() {
        assertEquals("_", Bookmark.UNKNOWN_TIME.toRelativeTime())
    }

    @Test
    fun underOneMinute_isJustNow() {
        assertEquals("just now", ago(30_000L).toRelativeTime())
    }

    @Test
    fun minutesBucket() {
        assertEquals("5m ago", ago(5 * 60_000L).toRelativeTime())
    }

    @Test
    fun hoursBucket() {
        assertEquals("3h ago", ago(3 * 3_600_000L).toRelativeTime())
    }

    @Test
    fun daysBucket() {
        assertEquals("2d ago", ago(2 * 86_400_000L).toRelativeTime())
    }

    @Test
    fun weeksBucket() {
        assertEquals("2w ago", ago(2 * 604_800_000L).toRelativeTime())
    }

    @Test
    fun monthsBucket() {
        assertEquals("3mo ago", ago(3 * 2_592_000_000L).toRelativeTime())
    }
}
