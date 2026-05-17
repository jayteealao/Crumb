package com.github.jayteealao.reddit.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.dropbox.differ.SimpleImageComparator
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
class RedditBookmarksScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val options = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(
            imageComparator = SimpleImageComparator(maxDistance = 0.01f),
        ),
    )

    @Test
    fun redditBookmarksScreen_loggedOut_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                RedditBookmarksScreen(
                    uiState = RedditBookmarksUiState(loggedIn = false),
                    pagedPosts = null,
                    onCardClick = {},
                    onLongPress = { _, _ -> },
                    onLoadTags = {},
                    onConnectClick = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/RedditBookmarksScreen_loggedOut_light.png", options)
    }

    @Test
    fun redditBookmarksScreen_loggedOut_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                RedditBookmarksScreen(
                    uiState = RedditBookmarksUiState(loggedIn = false),
                    pagedPosts = null,
                    onCardClick = {},
                    onLongPress = { _, _ -> },
                    onLoadTags = {},
                    onConnectClick = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/RedditBookmarksScreen_loggedOut_dark.png", options)
    }
}
