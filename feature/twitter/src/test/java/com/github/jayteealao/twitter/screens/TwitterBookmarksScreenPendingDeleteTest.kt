package com.github.jayteealao.twitter.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.dropbox.differ.SimpleImageComparator
import com.github.jayteealao.crumbs.designsystem.components.CrumbsBookmarkCard
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.models.ContentType
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi snapshots for the Twitter bookmarks feed when one or more rows are
 * in the `pending_delete` state. Renders the feed body directly with hand-rolled
 * bookmark data so the test avoids the LazyPagingItems / Hilt scaffolding of
 * the full screen — the strikethrough + swipe-box visuals all live in
 * `CrumbsBookmarkCard` and are independent of the paging source.
 *
 * Captures 4 PNGs: one pending row + one normal row, in light and dark themes,
 * plus a baseline pair without any pending rows. The non-pending pair guards
 * against pixel drift from the new params' defaults on every other call-site
 * (Reddit, AllBookmarks).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class TwitterBookmarksScreenPendingDeleteTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val options = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(
            imageComparator = SimpleImageComparator(maxDistance = 0.01f),
        ),
    )

    private val pendingBookmark = Bookmark(
        id = "tweet-pending-1",
        source = BookmarkSource.Twitter,
        author = "@crumbs_test",
        title = "Pending removal — swipe to confirm",
        previewText = "X removed this bookmark. Swipe right to confirm, left to keep it.",
        contentType = ContentType.Text,
        savedAt = 1747526400000L,
        tags = listOf("design"),
        pendingDelete = true,
        sourceUrl = "https://twitter.com/i/web/status/tweet-pending-1",
    )

    private val keptBookmark = Bookmark(
        id = "tweet-kept-1",
        source = BookmarkSource.Twitter,
        author = "@crumbs_test",
        title = "Brutalist design system applied to bookmarks",
        previewText = "Mono everything. Ink-stroked borders. No Material ripples.",
        contentType = ContentType.Text,
        savedAt = 1747526460000L,
        tags = listOf("design", "brutalist"),
        sourceUrl = "https://twitter.com/i/web/status/tweet-kept-1",
    )

    @Test
    fun withPendingDelete_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                Feed(showPending = true)
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/TwitterBookmarksScreen_pendingDelete_light.png",
                options,
            )
    }

    @Test
    fun withPendingDelete_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                Feed(showPending = true)
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/TwitterBookmarksScreen_pendingDelete_dark.png",
                options,
            )
    }

    @Test
    fun noPendingDelete_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                Feed(showPending = false)
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/TwitterBookmarksScreen_feedNoPendingDelete_light.png",
                options,
            )
    }

    @Test
    fun noPendingDelete_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                Feed(showPending = false)
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/TwitterBookmarksScreen_feedNoPendingDelete_dark.png",
                options,
            )
    }

    @androidx.compose.runtime.Composable
    private fun Feed(showPending: Boolean) {
        val rows = if (showPending) listOf(pendingBookmark, keptBookmark) else listOf(keptBookmark)
        val colors = LocalCrumbsColors.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(16.dp),
        ) {
            rows.forEach { bookmark ->
                CrumbsBookmarkCard(
                    bookmark = bookmark,
                    onCardClick = {},
                    onConfirmDeletePending = {},
                    onCancelDeletePending = {},
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
    }
}
