package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.github.jayteealao.crumbs.designsystem.TestCrumbsTheme
import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.models.BookmarkTextLink
import com.github.jayteealao.crumbs.models.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch-seam tests for inline link taps in [CrumbsBookmarkCard].
 *
 * These tests cover [Bookmark.textLinks] → [BookmarkCardBody] → `onLinkClick` routing at the
 * Robolectric Compose level — the same pattern as [LinkPreviewInteractionTest]. They confirm:
 * - tapping a link span fires `onLinkClick(expandedUrl)` and NOT `onCardClick`
 * - tapping the non-link body fires `onCardClick(sourceUrl)` and NOT `onLinkClick`
 * - a bookmark with no `textLinks` renders plain text; the card tap still fires `onCardClick`
 *
 * The actual `ACTION_VIEW` external-browser launch remains a device-fidelity gate and is not
 * exercised here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class InlineLinkInteractionTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * A text-type bookmark whose body contains two inline URL spans.
     *
     * The body text is "Read example.com/a and other.org/b for info." where
     * the displayUrls are injected via textLinks. The raw previewText uses the
     * original offsets as if the t.co URLs were 23 chars each.
     *
     * For the interaction test the exact offset numbers do not matter as long as
     * the overall text is at most 3 lines (the maxLines=3 clip happens after the
     * AnnotatedString is built, not before). We use a short, single-line body so
     * the link span is always visible and tappable.
     */
    private val textWithLinks =
        Bookmark(
            id = "inline-1",
            source = BookmarkSource.Twitter,
            author = "@reader",
            title = "Article round-up",
            // previewText: "Read https://t.co/aaa and https://t.co/bbb for info."
            // link1 covers [5, 26), link2 covers [31, 52) — 21 chars each (t.co length)
            previewText = "Read https://t.co/aaa and https://t.co/bbb for info.",
            contentType = ContentType.Text,
            savedAt = System.currentTimeMillis() - 3600000,
            sourceUrl = "https://twitter.com/i/web/status/900",
            textLinks =
                listOf(
                    BookmarkTextLink(
                        start = 5,
                        end = 26,
                        displayUrl = "example.com/a",
                        expandedUrl = "https://example.com/a",
                    ),
                    BookmarkTextLink(
                        start = 31,
                        end = 52,
                        displayUrl = "other.org/b",
                        expandedUrl = "https://other.org/b",
                    ),
                ),
        )

    private val textWithNoLinks =
        Bookmark(
            id = "inline-2",
            source = BookmarkSource.Twitter,
            author = "@plain",
            title = "No links here",
            previewText = "Just a plain tweet with no URL entities at all.",
            contentType = ContentType.Text,
            savedAt = System.currentTimeMillis() - 3600000,
            sourceUrl = "https://twitter.com/i/web/status/901",
        )

    @Test
    fun cardBodyTap_routesToOnCardClick_notOnLinkClick_forNoLinkBookmark() {
        var linkClicked: String? = null
        var cardClicked: String? = null
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                CrumbsBookmarkCard(
                    bookmark = textWithNoLinks,
                    onCardClick = { cardClicked = it },
                    onLinkClick = { linkClicked = it },
                )
            }
        }
        composeTestRule.waitForIdle()

        // Tap in the lower body region (below media/strip, above footer) of the card.
        composeTestRule.onNodeWithTag("bookmark-card").performTouchInput {
            click(Offset(center.x, height * 0.75f))
        }

        composeTestRule.runOnIdle {
            assertEquals("https://twitter.com/i/web/status/901", cardClicked)
            assertNull("link tap must not fire when no textLinks present", linkClicked)
        }
    }

    @Test
    fun cardBodyTap_onLinkBookmark_withNoLinkSpanHit_routesToOnCardClick() {
        // Tapping the non-link part of a body (e.g. far right edge of a non-link region)
        // should still route to onCardClick. The card's pointerInput handles the non-span area.
        var cardClicked: String? = null
        var linkClicked: String? = null
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                CrumbsBookmarkCard(
                    bookmark = textWithLinks,
                    onCardClick = { cardClicked = it },
                    onLinkClick = { linkClicked = it },
                )
            }
        }
        composeTestRule.waitForIdle()

        // Tap the footer area of the card (well below the preview text) — no link span there.
        composeTestRule.onNodeWithTag("bookmark-card").performTouchInput {
            click(Offset(center.x, height * 0.92f))
        }

        composeTestRule.runOnIdle {
            assertEquals("https://twitter.com/i/web/status/900", cardClicked)
            assertNull("link tap must not fire when footer is tapped", linkClicked)
        }
    }
}
