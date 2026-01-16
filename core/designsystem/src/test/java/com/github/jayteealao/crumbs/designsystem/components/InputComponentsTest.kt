package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
class InputComponentsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun textField_filled_default_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsTextField(
                    value = "",
                    onValueChange = {},
                    label = "Label",
                    placeholder = "Enter text",
                    style = TextFieldStyle.Filled
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTextField_filled_default_light.png")
    }

    @Test
    fun textField_filled_default_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsTextField(
                    value = "Sample text",
                    onValueChange = {},
                    label = "Label",
                    style = TextFieldStyle.Filled
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTextField_filled_default_dark.png")
    }

    @Test
    fun textField_filled_withLabel_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsTextField(
                    value = "",
                    onValueChange = {},
                    label = "Email Address",
                    placeholder = "your@email.com",
                    style = TextFieldStyle.Filled
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTextField_filled_withLabel_light.png")
    }

    @Test
    fun textField_filled_withIcons_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsTextField(
                    value = "Search query",
                    onValueChange = {},
                    placeholder = "Search...",
                    leadingIcon = {
                        Icon(Icons.Default.Search, "Search")
                    },
                    trailingIcon = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Clear, "Clear")
                        }
                    },
                    style = TextFieldStyle.Filled
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTextField_filled_withIcons_light.png")
    }

    @Test
    fun textField_filled_error_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsTextField(
                    value = "invalid@",
                    onValueChange = {},
                    label = "Email",
                    supportingText = "Please enter a valid email address",
                    isError = true,
                    style = TextFieldStyle.Filled
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTextField_filled_error_light.png")
    }

    @Test
    fun textField_filled_error_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsTextField(
                    value = "invalid@",
                    onValueChange = {},
                    label = "Email",
                    supportingText = "Please enter a valid email address",
                    isError = true,
                    style = TextFieldStyle.Filled
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTextField_filled_error_dark.png")
    }

    @Test
    fun textField_filled_disabled_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsTextField(
                    value = "Disabled field",
                    onValueChange = {},
                    label = "Label",
                    enabled = false,
                    style = TextFieldStyle.Filled
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTextField_filled_disabled_light.png")
    }

    @Test
    fun textField_outlined_default_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsTextField(
                    value = "",
                    onValueChange = {},
                    label = "Label",
                    placeholder = "Enter text",
                    style = TextFieldStyle.Outlined
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTextField_outlined_default_light.png")
    }

    @Test
    fun textField_outlined_default_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsTextField(
                    value = "Sample text",
                    onValueChange = {},
                    label = "Label",
                    style = TextFieldStyle.Outlined
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTextField_outlined_default_dark.png")
    }

    @Test
    fun textField_outlined_withIcons_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsTextField(
                    value = "Search query",
                    onValueChange = {},
                    placeholder = "Search...",
                    leadingIcon = {
                        Icon(Icons.Default.Search, "Search")
                    },
                    trailingIcon = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Clear, "Clear")
                        }
                    },
                    style = TextFieldStyle.Outlined
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTextField_outlined_withIcons_light.png")
    }

    @Test
    fun textField_outlined_error_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsTextField(
                    value = "invalid@",
                    onValueChange = {},
                    label = "Email",
                    supportingText = "Please enter a valid email address",
                    isError = true,
                    style = TextFieldStyle.Outlined
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTextField_outlined_error_light.png")
    }

    @Test
    fun textField_outlined_disabled_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsTextField(
                    value = "Disabled field",
                    onValueChange = {},
                    label = "Label",
                    enabled = false,
                    style = TextFieldStyle.Outlined
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTextField_outlined_disabled_light.png")
    }
}
