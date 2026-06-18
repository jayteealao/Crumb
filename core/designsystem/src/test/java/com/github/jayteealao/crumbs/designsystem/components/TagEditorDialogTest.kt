package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
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
@Config(sdk = [34])
class TagEditorDialogTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tagEditorDialog_withCurrentTags_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                TagEditorDialog(
                    isVisible = true,
                    currentTags = persistentListOf("kotlin", "compose"),
                    availableTags = persistentListOf("kotlin", "compose", "android", "design"),
                    onDismiss = {},
                    onSave = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/TagEditorDialog_withCurrentTags_light.png")
    }

    @Test
    fun tagEditorDialog_withCurrentTags_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                TagEditorDialog(
                    isVisible = true,
                    currentTags = persistentListOf("kotlin", "compose"),
                    availableTags = persistentListOf("kotlin", "compose", "android", "design"),
                    onDismiss = {},
                    onSave = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/TagEditorDialog_withCurrentTags_dark.png")
    }

    @Test
    fun tagEditorDialog_emptyTags_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                TagEditorDialog(
                    isVisible = true,
                    currentTags = persistentListOf(),
                    availableTags = persistentListOf("kotlin", "compose", "android"),
                    onDismiss = {},
                    onSave = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/TagEditorDialog_emptyTags_light.png")
    }
}
