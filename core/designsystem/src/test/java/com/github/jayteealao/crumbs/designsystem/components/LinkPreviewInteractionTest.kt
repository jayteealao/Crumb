package com.github.jayteealao.crumbs.designsystem.components

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.test.FakeImageLoaderEngine
import com.github.jayteealao.crumbs.designsystem.TestCrumbsTheme
import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.models.ContentType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Behavioural tests for the link-preview tap routing on [CrumbsBookmarkCard].
 *
 * The Roborazzi goldens in [CardComponentsTest] prove the preview *renders* in both the
 * rich (title + OG image + domain) and URL-only states. These tests cover the other half
 * of the acceptance criterion — *"when tapped, the link opens"* — at the dispatch seam that
 * is observable without a device: tapping the preview panel routes to `onLinkClick(linkUrl)`,
 * while tapping the rest of the card routes to `onCardClick(sourceUrl)` (the tweet permalink).
 * The actual `ACTION_VIEW` external-browser launch and the live synced-feed render remain
 * device-fidelity gates (the feed list is server-auth gated) and are exercised by the Maestro
 * flow, not here.
 *
 * A synchronous [FakeImageLoaderEngine] resolves the OG image request to a solid drawable so
 * the rich preview composes deterministically off-device.
 */
@OptIn(ExperimentalCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LinkPreviewInteractionTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val richLinkBookmark = Bookmark(
        id = "link-1",
        source = BookmarkSource.Twitter,
        author = "@reader",
        title = "Worth a read on brutalist design",
        previewText = "Sharing this great piece on raw, honest web interfaces.",
        contentType = ContentType.Link,
        savedAt = System.currentTimeMillis() - 3600000,
        sourceUrl = "https://twitter.com/i/web/status/205",
        linkUrl = "https://brutalist-web.design/",
        linkDisplayUrl = "brutalist-web.design",
        linkTitle = "Guidelines for Brutalist Web Design",
        linkDescription = "Raw content, honest materials, and a focus on the reader over decoration.",
        linkImageUrl = "https://img/og.jpg",
    )

    private val urlOnlyLinkBookmark = Bookmark(
        id = "link-2",
        source = BookmarkSource.Twitter,
        author = "@reader",
        title = "A link with no preview metadata",
        previewText = "When OG fetch yields nothing, the card degrades to a URL-only chip.",
        contentType = ContentType.Link,
        savedAt = System.currentTimeMillis() - 3600000,
        sourceUrl = "https://twitter.com/i/web/status/206",
        linkUrl = "https://example.com/article",
        linkDisplayUrl = "example.com/article",
    )

    @Before
    fun installFakeImageLoader() {
        val engine = FakeImageLoaderEngine.Builder()
            .default(ColorDrawable(Color.rgb(0x88, 0x88, 0x88)))
            .build()
        val imageLoader = ImageLoader.Builder(ApplicationProvider.getApplicationContext())
            .components { add(engine) }
            .build()
        Coil.setImageLoader(imageLoader)
    }

    @After
    fun resetImageLoader() {
        Coil.reset()
    }

    @Test
    fun previewTap_routesToOnLinkClick_withLinkUrl_notOnCardClick() {
        var linkClicked: String? = null
        var cardClicked: String? = null
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                CrumbsBookmarkCard(
                    bookmark = richLinkBookmark,
                    onCardClick = { cardClicked = it },
                    onLinkClick = { linkClicked = it },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("bookmark-card-link-preview").performClick()

        composeTestRule.runOnIdle {
            assertEquals("https://brutalist-web.design/", linkClicked)
            assertNull("card tap must not fire when the preview is tapped", cardClicked)
        }
    }

    @Test
    fun urlOnlyChipTap_routesToOnLinkClick_withLinkUrl() {
        var linkClicked: String? = null
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                CrumbsBookmarkCard(
                    bookmark = urlOnlyLinkBookmark,
                    onCardClick = {},
                    onLinkClick = { linkClicked = it },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("bookmark-card-link-preview").performClick()

        composeTestRule.runOnIdle {
            assertEquals("https://example.com/article", linkClicked)
        }
    }

    @Test
    fun cardBodyTap_routesToOnCardClick_withSourceUrl_notOnLinkClick() {
        var linkClicked: String? = null
        var cardClicked: String? = null
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                CrumbsBookmarkCard(
                    bookmark = richLinkBookmark,
                    onCardClick = { cardClicked = it },
                    onLinkClick = { linkClicked = it },
                )
            }
        }
        composeTestRule.waitForIdle()

        // Tap low in the card (footer/body region), clear of the top media band that holds
        // the preview panel, so the gesture lands on the card's own tap target.
        composeTestRule.onNodeWithTag("bookmark-card").performTouchInput {
            click(Offset(center.x, height * 0.92f))
        }

        composeTestRule.runOnIdle {
            assertEquals("https://twitter.com/i/web/status/205", cardClicked)
            assertNull("preview tap must not fire when the card body is tapped", linkClicked)
        }
    }
}
