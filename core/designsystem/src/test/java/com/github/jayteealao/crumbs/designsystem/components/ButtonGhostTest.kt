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
class ButtonGhostTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun button_ghost_medium_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsButton(
                    onClick = {},
                    text = "Ghost",
                    style = CrumbsButtonVariant.Ghost,
                    size = ButtonSize.Medium,
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsButton_ghost_medium_light.png")
    }

    @Test
    fun button_ghost_medium_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsButton(
                    onClick = {},
                    text = "Ghost",
                    style = CrumbsButtonVariant.Ghost,
                    size = ButtonSize.Medium,
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsButton_ghost_medium_dark.png")
    }

    @Test
    fun button_ghost_small_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsButton(
                    onClick = {},
                    text = "Ghost",
                    style = CrumbsButtonVariant.Ghost,
                    size = ButtonSize.Small,
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsButton_ghost_small_light.png")
    }
}
