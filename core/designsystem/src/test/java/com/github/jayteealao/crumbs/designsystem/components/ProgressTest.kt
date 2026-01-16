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
class ProgressTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun progress_circular_medium_indeterminate_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsProgressIndicator(
                    style = ProgressStyle.Circular,
                    size = ProgressSize.Medium
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsProgressIndicator_circular_medium_indeterminate_light.png")
    }

    @Test
    fun progress_circular_medium_indeterminate_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsProgressIndicator(
                    style = ProgressStyle.Circular,
                    size = ProgressSize.Medium
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsProgressIndicator_circular_medium_indeterminate_dark.png")
    }

    @Test
    fun progress_circular_small_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsProgressIndicator(
                    style = ProgressStyle.Circular,
                    size = ProgressSize.Small
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsProgressIndicator_circular_small_light.png")
    }

    @Test
    fun progress_circular_large_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsProgressIndicator(
                    style = ProgressStyle.Circular,
                    size = ProgressSize.Large
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsProgressIndicator_circular_large_light.png")
    }

    @Test
    fun progress_circular_medium_determinate_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsProgressIndicator(
                    style = ProgressStyle.Circular,
                    size = ProgressSize.Medium,
                    progress = 0.65f
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsProgressIndicator_circular_medium_determinate_light.png")
    }

    @Test
    fun progress_linear_medium_indeterminate_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsProgressIndicator(
                    style = ProgressStyle.Linear,
                    size = ProgressSize.Medium
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsProgressIndicator_linear_medium_indeterminate_light.png")
    }

    @Test
    fun progress_linear_medium_determinate_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsProgressIndicator(
                    style = ProgressStyle.Linear,
                    size = ProgressSize.Medium,
                    progress = 0.75f
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsProgressIndicator_linear_medium_determinate_light.png")
    }

    @Test
    fun progress_linear_large_determinate_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsProgressIndicator(
                    style = ProgressStyle.Linear,
                    size = ProgressSize.Large,
                    progress = 0.45f
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsProgressIndicator_linear_large_determinate_dark.png")
    }
}
