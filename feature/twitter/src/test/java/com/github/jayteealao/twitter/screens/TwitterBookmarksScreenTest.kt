package com.github.jayteealao.twitter.screens

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
class TwitterBookmarksScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val options = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(
            imageComparator = SimpleImageComparator(maxDistance = 0.01f),
        ),
    )

    @Test
    fun twitterBookmarksScreen_loggedOut_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                TwitterBookmarksScreen(
                    uiState = TwitterBookmarksUiState(loggedIn = false),
                    pagedBookmarks = null,
                    onCardClick = {},
                    onLongPress = { _, _ -> },
                    onLoadTags = {},
                    onLoadTagsForIds = {},
                    onRefresh = {},
                    onConnectClick = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/TwitterBookmarksScreen_loggedOut_light.png", options)
    }

    @Test
    fun twitterBookmarksScreen_loggedOut_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                TwitterBookmarksScreen(
                    uiState = TwitterBookmarksUiState(loggedIn = false),
                    pagedBookmarks = null,
                    onCardClick = {},
                    onLongPress = { _, _ -> },
                    onLoadTags = {},
                    onLoadTagsForIds = {},
                    onRefresh = {},
                    onConnectClick = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/TwitterBookmarksScreen_loggedOut_dark.png", options)
    }
}
