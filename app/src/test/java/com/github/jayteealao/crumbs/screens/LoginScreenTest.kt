package com.github.jayteealao.crumbs.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.screens.login.LoginScreen
import com.github.jayteealao.crumbs.screens.login.LoginUiState
import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class LoginScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val options = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(
            imageComparator = SimpleImageComparator(maxDistance = 0.01f),
        ),
    )

    @Test
    fun loginScreen_default_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                LoginScreen(
                    uiState = LoginUiState(),
                    onConnectTwitter = {},
                    onConnectReddit = {},
                    onSkipAuth = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/LoginScreen_default_light.png", options)
    }

    @Test
    fun loginScreen_default_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                LoginScreen(
                    uiState = LoginUiState(),
                    onConnectTwitter = {},
                    onConnectReddit = {},
                    onSkipAuth = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/LoginScreen_default_dark.png", options)
    }

    // Regression-check that OAuth-trigger callbacks survive the rewrite (slice spec line 71).
    @Test
    fun loginScreen_connectTwitter_invokesCallback() {
        var fired = false
        composeTestRule.setContent {
            CrumbsTheme {
                LoginScreen(
                    uiState = LoginUiState(),
                    onConnectTwitter = { fired = true },
                    onConnectReddit = {},
                    onSkipAuth = {},
                )
            }
        }
        composeTestRule.onNodeWithTag("login-twitter-cta").performClick()
        assertTrue(fired)
    }

    @Test
    fun loginScreen_connectReddit_invokesCallback() {
        var fired = false
        composeTestRule.setContent {
            CrumbsTheme {
                LoginScreen(
                    uiState = LoginUiState(),
                    onConnectTwitter = {},
                    onConnectReddit = { fired = true },
                    onSkipAuth = {},
                )
            }
        }
        composeTestRule.onNodeWithTag("login-reddit-cta").performClick()
        assertTrue(fired)
    }
}
