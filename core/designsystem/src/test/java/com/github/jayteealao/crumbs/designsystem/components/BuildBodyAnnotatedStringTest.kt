package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.ui.graphics.Color
import com.github.jayteealao.crumbs.models.BookmarkTextLink
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pure unit tests for [buildBodyAnnotatedString].
 *
 * These tests do NOT require a running Compose environment — [AnnotatedString] is a value type
 * that can be constructed and inspected on the JVM. Robolectric is used only to satisfy the
 * Android runtime needed for the Compose text library to link; no UI is rendered.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BuildBodyAnnotatedStringTest {

    private val accent = Color(0xFF_FF5A1Fu)

    private fun link(
        start: Int,
        end: Int,
        displayUrl: String,
        expandedUrl: String = "https://example.com/$displayUrl",
    ) = BookmarkTextLink(start, end, displayUrl, expandedUrl)

    // ── happy-path tests ──────────────────────────────────────────────────────

    @Test
    fun emptyTextLinks_returnsPlainAnnotatedString() {
        val result = buildBodyAnnotatedString("hello world", emptyList(), accent) {}
        assertEquals("hello world", result.text)
        assertEquals(0, result.spanStyles.size)
    }

    @Test
    fun singleLink_replacesSpanWithDisplayUrl() {
        // "Click https://t.co/abc here"
        //  01234 5         6          22 = end (exclusive) for "https://t.co/abc" which is 16 chars
        val text = "Click https://t.co/abc here"
        val result = buildBodyAnnotatedString(
            text = text,
            textLinks = listOf(link(6, 22, "example.com/article")),
            accentColor = accent,
            onLinkClick = {},
        )
        // The resulting text should be the prefix + displayUrl + suffix
        assertEquals("Click example.com/article here", result.text)
    }

    @Test
    fun multipleLinks_replacesAllSpansInOrder() {
        // "A https://t.co/x B https://t.co/y C"
        // link1: [2,16), link2: [19,33)
        val text = "A https://t.co/x B https://t.co/y C"
        val result = buildBodyAnnotatedString(
            text = text,
            textLinks = listOf(
                link(2, 16, "site1.com/a"),
                link(19, 33, "site2.org/b"),
            ),
            accentColor = accent,
            onLinkClick = {},
        )
        assertEquals("A site1.com/a B site2.org/b C", result.text)
    }

    @Test
    fun emojiBeforeLink_doesNotCrashAndProducesNonEmptyResult() {
        // Emoji takes 2 UTF-16 code units; API v2 treats it as 1 code point.
        // The guard must not crash even if the offset is slightly off.
        // "🔥 check https://t.co/x out"
        // emoji is chars [0,2), space [2,3), "check " [3,9), link [9,22), " out" [22,26)
        // Simulate API v2 offset that is 1 short due to emoji (start=8 instead of 9):
        val text = "🔥 check https://t.co/x out"
        val result = buildBodyAnnotatedString(
            text = text,
            textLinks = listOf(link(8, 21, "example.com/x")), // possibly 1 short due to surrogate
            accentColor = accent,
            onLinkClick = {},
        )
        // Should not throw; result text should be non-empty
        assert(result.text.isNotEmpty())
    }

    @Test
    fun outOfRangeOffset_isSkippedSilentlyWithNocrash() {
        // start=200, end=250 in a 30-char string → should be clamped and skipped (s >= e after clamp)
        val text = "short text with no real link"
        val result = buildBodyAnnotatedString(
            text = text,
            textLinks = listOf(link(200, 250, "overflow.com")),
            accentColor = accent,
            onLinkClick = {},
        )
        // Falls back to the original text unchanged (the malformed span is skipped)
        assertEquals(text, result.text)
    }

    @Test
    fun linkListenerRegisteredWithoutCrash_andTextContainsDisplayUrl() {
        // The lambda is registered on the LinkAnnotation; we can't fire it in a pure JVM test
        // without a Compose runtime dispatching a tap — this test confirms the helper builds
        // the AnnotatedString correctly and substitutes the displayUrl in the visible text.
        // The dispatch seam is exercised by InlineLinkInteractionTest (Robolectric Compose).
        // "Visit https://t.co/abc today"
        //  0     6                22  — "https://t.co/abc" is 16 chars, end=22 (exclusive)
        var clicked: String? = null
        val text = "Visit https://t.co/abc today"
        val result = buildBodyAnnotatedString(
            text = text,
            textLinks = listOf(
                BookmarkTextLink(6, 22, "example.com/page", "https://example.com/page"),
            ),
            accentColor = accent,
            onLinkClick = { clicked = it },
        )
        assertEquals("Visit example.com/page today", result.text)
        // clicked remains null until a real tap fires the listener
        assertEquals(null, clicked)
    }
}
