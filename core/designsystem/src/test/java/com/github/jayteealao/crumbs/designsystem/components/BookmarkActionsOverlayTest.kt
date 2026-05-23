package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.dropbox.differ.SimpleImageComparator
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.models.ContentType
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class BookmarkActionsOverlayTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val options = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(
            imageComparator = SimpleImageComparator(maxDistance = 0.01f),
        ),
    )

    private val sample = Bookmark(
        id = "1",
        source = BookmarkSource.Twitter,
        author = "@compose",
        title = "Sample bookmark",
        previewText = "Preview text",
        contentType = ContentType.Text,
        savedAt = 1730000000000L,
        sourceUrl = "https://example.com/1",
    )

    @Test
    fun bookmarkActionsOverlay_open_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                BookmarkActionsOverlay(
                    visible = true,
                    bookmark = sample,
                    currentTags = persistentListOf("android", "compose"),
                    availableTags = persistentListOf("android", "compose", "kotlin"),
                    onDismiss = {},
                    onActionSelect = {},
                    onTagsSave = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/BookmarkActionsOverlay_open_light.png", options)
    }

    @Test
    fun bookmarkActionsOverlay_open_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                BookmarkActionsOverlay(
                    visible = true,
                    bookmark = sample,
                    currentTags = persistentListOf("android"),
                    availableTags = persistentListOf("android", "compose", "kotlin"),
                    onDismiss = {},
                    onActionSelect = {},
                    onTagsSave = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/BookmarkActionsOverlay_open_dark.png", options)
    }
}
