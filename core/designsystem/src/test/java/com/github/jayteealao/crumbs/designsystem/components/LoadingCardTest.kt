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

// scanLinePositionFraction is pinned to 0.5f to bypass rememberInfiniteTransition;
// avoids the Roborazzi test-hang documented in issue #413.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LoadingCardTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loading_hasImage_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                LoadingCard(hasImage = true, scanLinePositionFraction = 0.5f)
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/LoadingCard_hasImage_light.png")
    }

    @Test
    fun loading_hasImage_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                LoadingCard(hasImage = true, scanLinePositionFraction = 0.5f)
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/LoadingCard_hasImage_dark.png")
    }

    @Test
    fun loading_noImage_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                LoadingCard(hasImage = false, scanLinePositionFraction = 0.5f)
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/LoadingCard_noImage_light.png")
    }

    @Test
    fun loading_noImage_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                LoadingCard(hasImage = false, scanLinePositionFraction = 0.5f)
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/LoadingCard_noImage_dark.png")
    }
}
