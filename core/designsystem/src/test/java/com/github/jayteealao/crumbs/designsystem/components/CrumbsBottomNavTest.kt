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
class CrumbsBottomNavTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun bottomNav_twitterSelected_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsBottomNav(
                    selectedTab = BottomNavTab.TWITTER,
                    onTabSelected = {}
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsBottomNav_twitterSelected_light.png")
    }

    @Test
    fun bottomNav_twitterSelected_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsBottomNav(
                    selectedTab = BottomNavTab.TWITTER,
                    onTabSelected = {}
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsBottomNav_twitterSelected_dark.png")
    }

    @Test
    fun bottomNav_redditSelected_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsBottomNav(
                    selectedTab = BottomNavTab.REDDIT,
                    onTabSelected = {}
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsBottomNav_redditSelected_light.png")
    }

    @Test
    fun bottomNav_allSelected_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsBottomNav(
                    selectedTab = BottomNavTab.ALL,
                    onTabSelected = {}
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsBottomNav_allSelected_light.png")
    }

    @Test
    fun bottomNav_mapSelected_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsBottomNav(
                    selectedTab = BottomNavTab.MAP,
                    onTabSelected = {}
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsBottomNav_mapSelected_light.png")
    }
}
