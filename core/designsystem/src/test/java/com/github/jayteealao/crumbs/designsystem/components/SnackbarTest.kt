package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SnackbarTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun snackbar_withAction_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsSnackbar(message = "Bookmark deleted", actionLabel = "Undo", onAction = {})
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsSnackbar_withAction_light.png")
    }

    @Test
    fun snackbar_withAction_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsSnackbar(message = "Bookmark deleted", actionLabel = "Undo", onAction = {})
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsSnackbar_withAction_dark.png")
    }

    @Test
    fun snackbar_noAction_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsSnackbar(message = "Sync complete")
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsSnackbar_noAction_light.png")
    }

    @Test
    fun snackbar_noAction_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsSnackbar(message = "Sync complete")
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsSnackbar_noAction_dark.png")
    }
}
