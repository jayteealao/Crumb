package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.TestCrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
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
 * Roborazzi screenshot tests for card components
 *
 * Tests CrumbsBookmarkCard variants and ThreadIndicator
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
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

    // ThreadIndicator Tests

    @Test
    fun threadIndicator_small_light() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                Surface(color = LocalCrumbsColors.current.background) {
                    Box(modifier = Modifier.padding(16.dp).wrapContentSize()) {
                        ThreadIndicator(threadCount = 2)
                    }
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/ThreadIndicator_small_light.png")
    }

    @Test
    fun threadIndicator_medium_light() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                Surface(color = LocalCrumbsColors.current.background) {
                    Box(modifier = Modifier.padding(16.dp).wrapContentSize()) {
                        ThreadIndicator(threadCount = 5)
                    }
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/ThreadIndicator_medium_light.png")
    }

    @Test
    fun threadIndicator_large_dark() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = true) {
                Surface(color = LocalCrumbsColors.current.background) {
                    Box(modifier = Modifier.padding(16.dp).wrapContentSize()) {
                        ThreadIndicator(threadCount = 20)
                    }
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/ThreadIndicator_large_dark.png")
    }
}
