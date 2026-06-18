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
 * Behavioural tests for the quoted sub-card tap routing on [CrumbsBookmarkCard].
 *
 * The Roborazzi goldens in [CardComponentsTest] prove the quoted block *renders* in
 * both the available and unavailable states. These cover the other half of the
 * acceptance criterion at the dispatch seam that is observable off-device: tapping the
 * quoted sub-card routes to `onQuoteClick(quotedTweetUrl)`, while tapping the rest of
 * the card routes to `onCardClick(sourceUrl)` (the parent permalink). The actual
 * `ACTION_VIEW` external-browser launch and the live synced-feed render remain
 * device-fidelity gates exercised by the Maestro flow, not here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class QuotedTweetInteractionTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val quoteBookmark = Bookmark(
        id = "quote-1",
        source = BookmarkSource.Twitter,
        author = "@commenter",
        title = "Adding my take on this",
        previewText = "This thread completely reframed how I think about it.",
        contentType = ContentType.Text,
        savedAt = System.currentTimeMillis() - 3600000,
        sourceUrl = "https://twitter.com/i/web/status/207",
        quotedTweetId = "999",
        quotedText = "The original insight everyone keeps quoting.",
        quotedAuthorName = "Original Author",
        quotedAuthorHandle = "@original",
        quotedTweetUrl = "https://twitter.com/original/status/999",
    )

    @Test
    fun quotedSubCardTap_routesToOnQuoteClick_withQuotedUrl_notOnCardClick() {
        var quoteClicked: String? = null
        var cardClicked: String? = null
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                CrumbsBookmarkCard(
                    bookmark = quoteBookmark,
                    onCardClick = { cardClicked = it },
                    onQuoteClick = { quoteClicked = it },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("bookmark-card-quoted-tweet").performClick()

        composeTestRule.runOnIdle {
            assertEquals("https://twitter.com/original/status/999", quoteClicked)
            assertNull("card tap must not fire when the quoted sub-card is tapped", cardClicked)
        }
    }

    @Test
    fun cardBodyTap_routesToOnCardClick_withSourceUrl_notOnQuoteClick() {
        var quoteClicked: String? = null
        var cardClicked: String? = null
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                CrumbsBookmarkCard(
                    bookmark = quoteBookmark,
                    onCardClick = { cardClicked = it },
                    onQuoteClick = { quoteClicked = it },
                )
            }
        }
        composeTestRule.waitForIdle()

        // Tap low in the card (footer region), clear of the quoted sub-card near the top,
        // so the gesture lands on the card's own tap target.
        composeTestRule.onNodeWithTag("bookmark-card").performTouchInput {
            click(Offset(center.x, height * 0.92f))
        }

        composeTestRule.runOnIdle {
            assertEquals("https://twitter.com/i/web/status/207", cardClicked)
            assertNull("quote tap must not fire when the card body is tapped", quoteClicked)
        }
    }
}
