package com.github.jayteealao.crumbs.designsystem.components

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.test.FakeImageLoaderEngine
import com.github.jayteealao.crumbs.designsystem.TestCrumbsTheme
import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.models.ContentType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi screenshot tests for CrumbsBookmarkCard variants.
 */
@OptIn(ExperimentalCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CardComponentsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // Deterministic Coil fake: every image request resolves to a solid mid-gray
    // drawable synchronously, so the single-image / 2×2 grid / "+N" goldens render
    // a stable, network-free bitmap instead of the accent loading placeholder.
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

    private val singleImageBookmark = Bookmark(
        id = "img-1",
        source = BookmarkSource.Twitter,
        author = "@photographer",
        title = "Single photo bookmark",
        previewText = "One image renders in the 16:7 media band.",
        imageUrl = "https://img/single.jpg",
        imageUrls = listOf("https://img/single.jpg"),
        contentType = ContentType.Image,
        savedAt = System.currentTimeMillis() - 3600000,
        sourceUrl = "https://twitter.com/i/web/status/200",
    )

    private val twoImageBookmark = Bookmark(
        id = "img-2",
        source = BookmarkSource.Twitter,
        author = "@gallery",
        title = "Two photo bookmark",
        previewText = "Two images render as a single grid row.",
        imageUrl = "https://img/1.jpg",
        imageUrls = listOf("https://img/1.jpg", "https://img/2.jpg"),
        contentType = ContentType.Image,
        savedAt = System.currentTimeMillis() - 3600000,
        sourceUrl = "https://twitter.com/i/web/status/201",
    )

    private val fourImageBookmark = Bookmark(
        id = "img-4",
        source = BookmarkSource.Twitter,
        author = "@gallery",
        title = "Four photo bookmark",
        previewText = "Four images render as a 2×2 grid.",
        imageUrl = "https://img/1.jpg",
        imageUrls = listOf(
            "https://img/1.jpg", "https://img/2.jpg",
            "https://img/3.jpg", "https://img/4.jpg",
        ),
        contentType = ContentType.Image,
        savedAt = System.currentTimeMillis() - 3600000,
        sourceUrl = "https://twitter.com/i/web/status/202",
    )

    private val overflowImageBookmark = Bookmark(
        id = "img-6",
        source = BookmarkSource.Twitter,
        author = "@gallery",
        title = "Six photo bookmark",
        previewText = "More than four images collapse to a +N scrim on the 4th tile.",
        imageUrl = "https://img/1.jpg",
        imageUrls = listOf(
            "https://img/1.jpg", "https://img/2.jpg", "https://img/3.jpg",
            "https://img/4.jpg", "https://img/5.jpg", "https://img/6.jpg",
        ),
        contentType = ContentType.Image,
        savedAt = System.currentTimeMillis() - 3600000,
        sourceUrl = "https://twitter.com/i/web/status/203",
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

    // Image media goldens — the fake Coil loader renders every photo as a solid
    // gray tile so layout (16:7 band, 2×2 grid, "+N" overflow scrim) is the subject.

    @Test
    fun bookmarkCard_singleImage_light() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                CrumbsBookmarkCard(bookmark = singleImageBookmark, onCardClick = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsBookmarkCard_singleImage_light.png")
    }

    @Test
    fun bookmarkCard_twoImageGrid_light() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                CrumbsBookmarkCard(bookmark = twoImageBookmark, onCardClick = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsBookmarkCard_twoImageGrid_light.png")
    }

    @Test
    fun bookmarkCard_fourImageGrid_light() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                CrumbsBookmarkCard(bookmark = fourImageBookmark, onCardClick = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsBookmarkCard_fourImageGrid_light.png")
    }

    @Test
    fun bookmarkCard_overflowImageGrid_light() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                CrumbsBookmarkCard(bookmark = overflowImageBookmark, onCardClick = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsBookmarkCard_overflowImageGrid_light.png")
    }

}
