package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.dropbox.differ.SimpleImageComparator
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.models.ContentType
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
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

    // AC2 — exactly one scrim (the OverlayShell hatched backdrop); no nested
    // CrumbsLongPressPopup means the `popup-scrim` node must be absent, while the
    // action cells render inline and remain addressable by their testTags.
    @Test
    fun overlay_has_single_scrim_with_inline_actions() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
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

        composeTestRule.onNodeWithTag("overlay-shell-backdrop").assertIsDisplayed()
        composeTestRule.onNodeWithTag("popup-scrim").assertDoesNotExist()
        composeTestRule.onNodeWithTag("popup-action-tag").assertIsDisplayed()
        composeTestRule.onNodeWithTag("popup-action-delete").assertIsDisplayed()
    }

    // AC3 — a single owning BackHandler (OverlayShell's) dismisses the overlay
    // exactly once; the removed inner Popup no longer double-pops.
    @Test
    fun back_press_dismisses_overlay_exactly_once() {
        var dismissCount = 0

        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                BookmarkActionsOverlay(
                    visible = true,
                    bookmark = sample,
                    currentTags = persistentListOf(),
                    availableTags = persistentListOf(),
                    onDismiss = { dismissCount++ },
                    onActionSelect = {},
                    onTagsSave = {},
                )
            }
        }

        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        assertEquals("Back must dismiss the overlay exactly once", 1, dismissCount)
    }

    // AC4 — opening the tag editor then clearing the active bookmark externally
    // (bookmark == null) must collapse the editor, so re-opening with a new
    // bookmark does not reopen it unprompted.
    @Test
    fun tag_editor_resets_when_bookmark_cleared() {
        var current by mutableStateOf<Bookmark?>(sample)

        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                BookmarkActionsOverlay(
                    visible = current != null,
                    bookmark = current,
                    currentTags = persistentListOf(),
                    availableTags = persistentListOf("android"),
                    onDismiss = { current = null },
                    onActionSelect = {},
                    onTagsSave = {},
                )
            }
        }

        // Open the tag editor via the inline affordance.
        composeTestRule.onNodeWithTag("bookmark-actions-add-tag").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tag-editor-dialog").assertIsDisplayed()

        // Clear the active bookmark externally, then re-open with a new one.
        composeTestRule.runOnIdle { current = null }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { current = sample }
        composeTestRule.waitForIdle()

        // The editor must NOT reappear on the fresh long-press.
        composeTestRule.onNodeWithTag("tag-editor-dialog").assertDoesNotExist()
    }
}
