package com.github.jayteealao.crumbs.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.dropbox.differ.SimpleImageComparator
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ConnectXOnboardingScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val options = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(
            imageComparator = SimpleImageComparator(maxDistance = 0.01f),
        ),
    )

    @Test
    fun connectX_default_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                ConnectXOnboardingScreen(onConnect = {}, onSkip = {})
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/ConnectXOnboardingScreen_default_light.png", options)
    }

    @Test
    fun connectX_default_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                ConnectXOnboardingScreen(onConnect = {}, onSkip = {})
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/ConnectXOnboardingScreen_default_dark.png", options)
    }

    @Test
    fun connectX_connecting_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                ConnectXOnboardingScreen(onConnect = {}, onSkip = {}, connecting = true)
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/ConnectXOnboardingScreen_connecting_light.png", options)
    }
}
