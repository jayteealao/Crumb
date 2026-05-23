package com.github.jayteealao.crumbs.screens

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
class ThreadDetailScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val options = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(
            imageComparator = SimpleImageComparator(maxDistance = 0.01f),
        ),
    )

    private fun root() = Bookmark(
        id = "root",
        source = BookmarkSource.Twitter,
        author = "@compose",
        title = "Brutalist thread root",
        previewText = "Root tweet preview text body content goes here.",
        contentType = ContentType.Thread,
        savedAt = 1730000000000L,
        isThread = true,
        threadCount = 3,
        sourceUrl = "https://twitter.com/compose/status/root",
    )

    private fun reply(id: String, text: String) = root().copy(
        id = id,
        previewText = text,
        contentType = ContentType.Text,
    )

    @Test
    fun threadDetail_loaded_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                ThreadDetailScreen(
                    uiState = ThreadDetailUiState.Loaded(
                        root = root(),
                        replies = persistentListOf(
                            reply("r1", "Reply one — short answer."),
                            reply("r2", "Reply two — slightly longer answer with more words."),
                        ),
                    ),
                    onClose = {},
                    onRootCardClick = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/ThreadDetailScreen_loaded_light.png", options)
    }

    @Test
    fun threadDetail_empty_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                ThreadDetailScreen(
                    uiState = ThreadDetailUiState.Loaded(
                        root = root(),
                        replies = persistentListOf(),
                    ),
                    onClose = {},
                    onRootCardClick = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/ThreadDetailScreen_empty_dark.png", options)
    }
}
