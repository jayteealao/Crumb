package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import com.github.jayteealao.crumbs.models.BookmarkTextLink

/**
 * Builds an [AnnotatedString] for the tweet body that replaces each t.co span identified by
 * [textLinks] with a clickable [displayUrl] rendered in [accentColor].
 *
 * When [textLinks] is empty the result is a plain [AnnotatedString] wrapping [text] unchanged —
 * the safe fallback for Reddit cards, pre-enrichment tweets, and any tweet with no URL entities.
 *
 * Processing order: links are collected in ascending [BookmarkTextLink.start] order and emitted
 * in a single forward pass over the original [text]. Descending-order pre-sort is NOT used here
 * because the builder emits spans incrementally from the original indices rather than mutating a
 * mutable string — ascending order is therefore both correct and natural. Each link's [start] and
 * [end] are clamped to `[0, text.length]` and skipped silently if `start >= end` (out-of-range
 * guard, covers emoji/surrogate misalignment).
 *
 * @param text The raw tweet body text (may contain t.co short URLs).
 * @param textLinks Inline URL entities sorted ascending by start offset.
 * @param accentColor Accent colour applied to each link span (typically `Color(0xFF_FF5A1F)`).
 * @param onLinkClick Called with the [BookmarkTextLink.expandedUrl] when the span is tapped.
 *   The caller is responsible for routing this to an `ACTION_VIEW` intent.
 */
fun buildBodyAnnotatedString(
    text: String,
    textLinks: List<BookmarkTextLink>,
    accentColor: Color,
    onLinkClick: (String) -> Unit,
): AnnotatedString {
    if (textLinks.isEmpty()) return AnnotatedString(text)

    return buildAnnotatedString {
        var cursor = 0
        // Sort ascending (mapper guarantees this, but be defensive).
        val sorted = textLinks.sortedBy { it.start }
        for (link in sorted) {
            // Clamp and guard out-of-range or zero-width spans.
            val s = link.start.coerceIn(0, text.length)
            val e = link.end.coerceIn(s, text.length)
            if (s >= e) continue // silently skip malformed span
            if (s > cursor) {
                append(text.substring(cursor, s))
            }
            withLink(
                LinkAnnotation.Url(
                    url = link.expandedUrl,
                    styles = TextLinkStyles(
                        style = SpanStyle(color = accentColor),
                    ),
                    linkInteractionListener = { onLinkClick(link.expandedUrl) },
                ),
            ) {
                append(link.displayUrl)
            }
            cursor = e
        }
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}
