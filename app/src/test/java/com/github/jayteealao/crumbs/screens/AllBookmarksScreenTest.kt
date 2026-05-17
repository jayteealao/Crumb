package com.github.jayteealao.crumbs.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
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
class AllBookmarksScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val options = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(
            imageComparator = SimpleImageComparator(maxDistance = 0.01f),
        ),
    )

    @Test
    fun allBookmarksScreen_empty_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                AllBookmarksScreen(
                    uiState = AllBookmarksUiState(),
                    twitterItems = null,
                    redditItems = null,
                    onCardClick = {},
                    onLongPress = { _, _ -> },
                    onConnectAccountClick = {},
                    onLoadTags = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/AllBookmarksScreen_empty_light.png", options)
    }

    @Test
    fun allBookmarksScreen_empty_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                AllBookmarksScreen(
                    uiState = AllBookmarksUiState(),
                    twitterItems = null,
                    redditItems = null,
                    onCardClick = {},
                    onLongPress = { _, _ -> },
                    onConnectAccountClick = {},
                    onLoadTags = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/AllBookmarksScreen_empty_dark.png", options)
    }

    // AC line 69 — empty-state CTA invokes the connect-account callback.
    @Test
    fun emptyState_connectAccountCta_invokesCallback() {
        var fired = false
        composeTestRule.setContent {
            CrumbsTheme {
                AllBookmarksScreen(
                    uiState = AllBookmarksUiState(),
                    twitterItems = null,
                    redditItems = null,
                    onCardClick = {},
                    onLongPress = { _, _ -> },
                    onConnectAccountClick = { fired = true },
                    onLoadTags = {},
                )
            }
        }
        composeTestRule.onNodeWithTag("empty-state-cta").performClick()
        assertTrue(fired)
    }
}
