package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
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
class DividerTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun divider_solid_regular_horizontal_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsDivider(
                    style = DividerStyle.Solid,
                    thickness = DividerThickness.Regular
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsDivider_solid_regular_horizontal_light.png")
    }

    @Test
    fun divider_solid_regular_horizontal_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsDivider(
                    style = DividerStyle.Solid,
                    thickness = DividerThickness.Regular
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsDivider_solid_regular_horizontal_dark.png")
    }

    @Test
    fun divider_dashed_thin_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsDivider(
                    style = DividerStyle.Dashed,
                    thickness = DividerThickness.Thin
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsDivider_dashed_thin_light.png")
    }

    @Test
    fun divider_dotted_regular_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsDivider(
                    style = DividerStyle.Dotted,
                    thickness = DividerThickness.Regular
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsDivider_dotted_regular_light.png")
    }

    @Test
    fun divider_solid_thick_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsDivider(
                    style = DividerStyle.Solid,
                    thickness = DividerThickness.Thick
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsDivider_solid_thick_light.png")
    }

    @Test
    fun divider_solid_regular_vertical_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                Box(modifier = Modifier.height(100.dp).width(100.dp)) {
                    CrumbsDivider(
                        style = DividerStyle.Solid,
                        thickness = DividerThickness.Regular,
                        horizontal = false
                    )
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsDivider_solid_regular_vertical_light.png")
    }
}
