package com.github.jayteealao.crumbs.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.jayteealao.crumbs.designsystem.components.BottomNavTab
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
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
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val options = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(
            imageComparator = SimpleImageComparator(maxDistance = 0.01f),
        ),
    )

    @Test
    fun homeScreen_twitter_light() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                HomeScreen(
                    uiState = HomeUiState(selectedTab = BottomNavTab.TWITTER, filterCount = 42),
                    onTabSelected = {},
                    onSearchQueryChange = {},
                    onSearchActiveChange = {},
                ) { _, _ ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(LocalCrumbsColors.current.surface),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/HomeScreen_twitter_light.png", options)
    }

    @Test
    fun homeScreen_all_dark() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                HomeScreen(
                    uiState = HomeUiState(selectedTab = BottomNavTab.ALL, filterCount = 13),
                    onTabSelected = {},
                    onSearchQueryChange = {},
                    onSearchActiveChange = {},
                ) { _, _ ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(LocalCrumbsColors.current.surface),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/HomeScreen_all_dark.png", options)
    }
}
