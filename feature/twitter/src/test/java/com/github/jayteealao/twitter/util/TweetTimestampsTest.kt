package com.github.jayteealao.twitter.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifies [parseTweetTimestamp] handles both corpus formats and returns null (rather than a
 * fabricated value) for anything it cannot parse. Epoch-0 anchors keep the expectations obvious.
 */
class TweetTimestampsTest {

    @Test
    fun isoZuluWithMillis() {
        assertEquals(0L, parseTweetTimestamp("1970-01-01T00:00:00.000Z"))
    }

    @Test
    fun isoZuluWithoutMillis() {
        assertEquals(1_000L, parseTweetTimestamp("1970-01-01T00:00:01Z"))
    }

    @Test
    fun isoWithExplicitOffset() {
        assertEquals(0L, parseTweetTimestamp("1970-01-01T01:00:00+01:00"))
    }

    @Test
    fun legacyV1_1Utc() {
        assertEquals(0L, parseTweetTimestamp("Thu Jan 01 00:00:00 +0000 1970"))
    }

    @Test
    fun legacyV1_1WithOffset() {
        assertEquals(0L, parseTweetTimestamp("Thu Jan 01 01:00:00 +0100 1970"))
    }

    @Test
    fun unparseableReturnsNull() {
        assertNull(parseTweetTimestamp("definitely not a date"))
    }

    @Test
    fun nullReturnsNull() {
        assertNull(parseTweetTimestamp(null))
    }

    @Test
    fun blankReturnsNull() {
        assertNull(parseTweetTimestamp("   "))
    }
}
