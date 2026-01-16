package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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
class ActionComponentsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun iconButton_filled_medium_enabled_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsIconButton(
                    onClick = {},
                    icon = { Icon(Icons.Default.Add, "Add") },
                    style = IconButtonStyle.Filled,
                    size = IconButtonSize.Medium
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsIconButton_filled_medium_enabled_light.png")
    }

    @Test
    fun iconButton_filled_medium_enabled_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsIconButton(
                    onClick = {},
                    icon = { Icon(Icons.Default.Add, "Add") },
                    style = IconButtonStyle.Filled,
                    size = IconButtonSize.Medium
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsIconButton_filled_medium_enabled_dark.png")
    }

    @Test
    fun iconButton_filled_small_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsIconButton(
                    onClick = {},
                    icon = { Icon(Icons.Default.Favorite, "Favorite") },
                    style = IconButtonStyle.Filled,
                    size = IconButtonSize.Small
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsIconButton_filled_small_light.png")
    }

    @Test
    fun iconButton_filled_large_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsIconButton(
                    onClick = {},
                    icon = { Icon(Icons.Default.Search, "Search") },
                    style = IconButtonStyle.Filled,
                    size = IconButtonSize.Large
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsIconButton_filled_large_light.png")
    }

    @Test
    fun iconButton_filledTonal_medium_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsIconButton(
                    onClick = {},
                    icon = { Icon(Icons.Default.Favorite, "Favorite") },
                    style = IconButtonStyle.FilledTonal,
                    size = IconButtonSize.Medium
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsIconButton_filledTonal_medium_light.png")
    }

    @Test
    fun iconButton_filledTonal_medium_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsIconButton(
                    onClick = {},
                    icon = { Icon(Icons.Default.Favorite, "Favorite") },
                    style = IconButtonStyle.FilledTonal,
                    size = IconButtonSize.Medium
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsIconButton_filledTonal_medium_dark.png")
    }

    @Test
    fun iconButton_outlined_medium_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsIconButton(
                    onClick = {},
                    icon = { Icon(Icons.Default.Search, "Search") },
                    style = IconButtonStyle.Outlined,
                    size = IconButtonSize.Medium
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsIconButton_outlined_medium_light.png")
    }

    @Test
    fun iconButton_outlined_large_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsIconButton(
                    onClick = {},
                    icon = { Icon(Icons.Default.Search, "Search") },
                    style = IconButtonStyle.Outlined,
                    size = IconButtonSize.Large
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsIconButton_outlined_large_dark.png")
    }

    @Test
    fun iconButton_standard_medium_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsIconButton(
                    onClick = {},
                    icon = { Icon(Icons.Default.Add, "Add") },
                    style = IconButtonStyle.Standard,
                    size = IconButtonSize.Medium
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsIconButton_standard_medium_light.png")
    }

    @Test
    fun iconButton_standard_medium_disabled_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsIconButton(
                    onClick = {},
                    icon = { Icon(Icons.Default.Add, "Add") },
                    style = IconButtonStyle.Standard,
                    size = IconButtonSize.Medium,
                    enabled = false
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsIconButton_standard_medium_disabled_light.png")
    }
}
