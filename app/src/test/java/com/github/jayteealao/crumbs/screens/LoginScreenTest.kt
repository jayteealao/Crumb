package com.github.jayteealao.crumbs.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
        // Behavioral: the wordmark and both connect CTAs are visible in the default unauthenticated state.
        composeTestRule.onNodeWithTag("login-wordmark").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login-google-cta").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login-twitter-cta").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login-reddit-cta").assertIsDisplayed()
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
        // Behavioral: the wordmark and both connect CTAs are visible in the default unauthenticated state (dark theme).
        composeTestRule.onNodeWithTag("login-wordmark").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login-google-cta").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login-twitter-cta").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login-reddit-cta").assertIsDisplayed()
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

    // Confirms the new Google CTA fires its callback (regression guard).
    @Test
    fun loginScreen_signInGoogle_invokesCallback() {
        var fired = false
        composeTestRule.setContent {
            CrumbsTheme {
                LoginScreen(
                    uiState = LoginUiState(),
                    onConnectTwitter = {},
                    onConnectReddit = {},
                    onSkipAuth = {},
                    onSignInWithGoogle = { fired = true },
                )
            }
        }
        composeTestRule.onNodeWithTag("login-google-cta").performClick()
        assertTrue(fired)
    }

    // Roborazzi snapshots for the Firebase auth surface — 4 states × 2 themes.

    @Test
    fun loginScreen_googlePrimary_light() {
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
        // Behavioral: Google CTA is the primary button and is enabled by default.
        composeTestRule.onNodeWithTag("login-google-cta").assertIsDisplayed()
        composeTestRule.onNodeWithText("CONTINUE WITH GOOGLE").assertIsDisplayed()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/LoginScreen_googlePrimary_light.png", options)
    }

    @Test
    fun loginScreen_googlePrimary_dark() {
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
        // Behavioral: Google CTA is the primary button and is enabled by default (dark theme).
        composeTestRule.onNodeWithTag("login-google-cta").assertIsDisplayed()
        composeTestRule.onNodeWithText("CONTINUE WITH GOOGLE").assertIsDisplayed()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/LoginScreen_googlePrimary_dark.png", options)
    }

    @Test
    fun loginScreen_collisionPrompt_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                LoginScreen(
                    uiState = LoginUiState(collisionPromptVisible = true),
                    onConnectTwitter = {},
                    onConnectReddit = {},
                    onSkipAuth = {},
                )
            }
        }
        // Behavioral: collision dialog is shown with the "EXISTING ACCOUNT FOUND" heading.
        composeTestRule.onNodeWithTag("login-email-dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("EXISTING ACCOUNT FOUND").assertIsDisplayed()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/LoginScreen_collisionPrompt_light.png", options)
    }

    @Test
    fun loginScreen_collisionPrompt_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                LoginScreen(
                    uiState = LoginUiState(collisionPromptVisible = true),
                    onConnectTwitter = {},
                    onConnectReddit = {},
                    onSkipAuth = {},
                )
            }
        }
        // Behavioral: collision dialog is shown with the "EXISTING ACCOUNT FOUND" heading (dark theme).
        composeTestRule.onNodeWithTag("login-email-dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("EXISTING ACCOUNT FOUND").assertIsDisplayed()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/LoginScreen_collisionPrompt_dark.png", options)
    }

    @Test
    fun loginScreen_emailDialog_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                LoginScreen(
                    uiState = LoginUiState(emailDialogVisible = true),
                    onConnectTwitter = {},
                    onConnectReddit = {},
                    onSkipAuth = {},
                )
            }
        }
        // Behavioral: email dialog is shown with the correct heading and input fields.
        composeTestRule.onNodeWithTag("login-email-dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("SIGN IN WITH EMAIL").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login-email-field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login-password-field").assertIsDisplayed()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/LoginScreen_emailDialog_light.png", options)
    }

    @Test
    fun loginScreen_emailDialog_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                LoginScreen(
                    uiState = LoginUiState(emailDialogVisible = true),
                    onConnectTwitter = {},
                    onConnectReddit = {},
                    onSkipAuth = {},
                )
            }
        }
        // Behavioral: email dialog is shown with the correct heading and input fields (dark theme).
        composeTestRule.onNodeWithTag("login-email-dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("SIGN IN WITH EMAIL").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login-email-field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login-password-field").assertIsDisplayed()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/LoginScreen_emailDialog_dark.png", options)
    }

    @Test
    fun loginScreen_signedIn_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                LoginScreen(
                    uiState = LoginUiState(firebaseSignedIn = true),
                    onConnectTwitter = {},
                    onConnectReddit = {},
                    onSkipAuth = {},
                )
            }
        }
        // Behavioral: when signed in, the "SIGN OUT" button replaces the Google CTA.
        composeTestRule.onNodeWithTag("login-firebase-signout").assertIsDisplayed()
        composeTestRule.onNodeWithText("SIGN OUT").assertIsDisplayed()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/LoginScreen_signedIn_light.png", options)
    }

    @Test
    fun loginScreen_signedIn_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                LoginScreen(
                    uiState = LoginUiState(firebaseSignedIn = true),
                    onConnectTwitter = {},
                    onConnectReddit = {},
                    onSkipAuth = {},
                )
            }
        }
        // Behavioral: when signed in, the "SIGN OUT" button replaces the Google CTA (dark theme).
        composeTestRule.onNodeWithTag("login-firebase-signout").assertIsDisplayed()
        composeTestRule.onNodeWithText("SIGN OUT").assertIsDisplayed()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/LoginScreen_signedIn_dark.png", options)
    }
}
