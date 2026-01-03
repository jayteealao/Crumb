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
@Config(sdk = [33])
class CrumbsButtonTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun button_primary_medium_enabled_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsButton(
                    onClick = {},
                    text = "Click Me",
                    style = ButtonStyle.Primary,
                    size = ButtonSize.Medium
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsButton_primary_medium_enabled_light.png")
    }

    @Test
    fun button_primary_medium_enabled_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsButton(
                    onClick = {},
                    text = "Click Me",
                    style = ButtonStyle.Primary,
                    size = ButtonSize.Medium
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsButton_primary_medium_enabled_dark.png")
    }

    @Test
    fun button_primary_medium_disabled_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsButton(
                    onClick = {},
                    text = "Disabled",
                    enabled = false,
                    style = ButtonStyle.Primary,
                    size = ButtonSize.Medium
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsButton_primary_medium_disabled_light.png")
    }

    @Test
    fun button_secondary_small_enabled_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsButton(
                    onClick = {},
                    text = "Small",
                    style = ButtonStyle.Secondary,
                    size = ButtonSize.Small
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsButton_secondary_small_enabled_light.png")
    }
}
