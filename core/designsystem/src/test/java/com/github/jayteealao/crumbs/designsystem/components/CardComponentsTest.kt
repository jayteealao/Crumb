package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.jayteealao.crumbs.designsystem.TestCrumbsTheme
import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.models.ContentType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi screenshot tests for CrumbsBookmarkCard variants.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CardComponentsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // Sample bookmark data
    private val twitterTextBookmark = Bookmark(
        id = "1",
        source = BookmarkSource.Twitter,
        author = "@designpatterns",
        title = "Understanding SOLID Principles",
        previewText = "Let me explain the five SOLID principles that every developer should know. These fundamental concepts will help you write better, more maintainable code.",
        contentType = ContentType.Text,
        savedAt = System.currentTimeMillis() - 3600000,
        tags = listOf("programming", "design"),
        sourceUrl = "https://twitter.com/i/web/status/123"
    )

    private val twitterThreadBookmark = Bookmark(
        id = "2",
        source = BookmarkSource.Twitter,
        author = "@architectpatterns",
        title = "Clean Architecture Thread",
        previewText = "1/ Let's talk about Clean Architecture and why it matters for modern Android development. This is going to be a detailed thread...",
        contentType = ContentType.Thread,
        savedAt = System.currentTimeMillis() - 86400000,
        tags = listOf("architecture", "android"),
        isThread = true,
        threadCount = 12,
        sourceUrl = "https://twitter.com/i/web/status/124"
    )

    private val redditPostBookmark = Bookmark(
        id = "3",
        source = BookmarkSource.Reddit,
        author = "u/androiddev",
        title = "Tips for optimizing RecyclerView performance",
        previewText = "Here are some lesser-known tips for getting better performance out of RecyclerView. These helped me reduce jank significantly in my production app.",
        contentType = ContentType.Text,
        savedAt = System.currentTimeMillis() - 172800000,
        tags = listOf("android", "performance"),
        sourceUrl = "https://reddit.com/r/androiddev/comments/abc123"
    )

    private val deletedBookmark = Bookmark(
        id = "4",
        source = BookmarkSource.Twitter,
        author = "@deleteduser",
        title = "This tweet has been deleted",
        previewText = "This content is no longer available.",
        contentType = ContentType.Text,
        savedAt = System.currentTimeMillis() - 604800000,
        isDeleted = true,
        sourceUrl = "https://twitter.com/i/web/status/125"
    )

    // CrumbsBookmarkCard Tests

    @Test
    fun bookmarkCard_twitterText_light() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                CrumbsBookmarkCard(
                    bookmark = twitterTextBookmark,
                    onCardClick = {}
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsBookmarkCard_twitterText_light.png")
    }

    @Test
    fun bookmarkCard_twitterText_dark() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = true) {
                CrumbsBookmarkCard(
                    bookmark = twitterTextBookmark,
                    onCardClick = {}
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsBookmarkCard_twitterText_dark.png")
    }

    @Test
    fun bookmarkCard_twitterThread_light() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                CrumbsBookmarkCard(
                    bookmark = twitterThreadBookmark,
                    onCardClick = {}
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsBookmarkCard_twitterThread_light.png")
    }

    @Test
    fun bookmarkCard_redditPost_light() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                CrumbsBookmarkCard(
                    bookmark = redditPostBookmark,
                    onCardClick = {}
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsBookmarkCard_redditPost_light.png")
    }

    @Test
    fun bookmarkCard_deleted_light() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                CrumbsBookmarkCard(
                    bookmark = deletedBookmark,
                    onCardClick = {}
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsBookmarkCard_deleted_light.png")
    }

    // Pins the index strip rendering a real, > 3-digit DB number (rowid) instead of the
    // default "000" — the value is passed pre-formatted through indexOverride, exactly as
    // the Twitter feed call site does (`"%03d".format(bookmark.dbNumber)`).
    @Test
    fun bookmarkCard_largeDbNumber_light() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                CrumbsBookmarkCard(
                    bookmark = twitterTextBookmark.copy(dbNumber = 1234L),
                    onCardClick = {},
                    indexOverride = "%03d".format(1234L),
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsBookmarkCard_largeDbNumber_light.png")
    }

}
