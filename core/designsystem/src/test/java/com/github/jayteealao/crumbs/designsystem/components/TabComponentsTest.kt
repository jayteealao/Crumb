package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
class TabComponentsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val sampleTabs = listOf(
        TabItem("Home", Icons.Default.Home),
        TabItem("Search", Icons.Default.Search),
        TabItem("Settings", Icons.Default.Settings)
    )

    @Test
    fun tabBar_standard_medium_selected_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsTabBar(
                    tabs = sampleTabs,
                    selectedIndex = 0,
                    onTabSelected = {},
                    style = TabBarStyle.Standard,
                    size = TabSize.Medium
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTabBar_standard_medium_selected_light.png")
    }

    @Test
    fun tabBar_standard_medium_selected_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsTabBar(
                    tabs = sampleTabs,
                    selectedIndex = 1,
                    onTabSelected = {},
                    style = TabBarStyle.Standard,
                    size = TabSize.Medium
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTabBar_standard_medium_selected_dark.png")
    }

    @Test
    fun tabBar_standard_small_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsTabBar(
                    tabs = sampleTabs,
                    selectedIndex = 0,
                    onTabSelected = {},
                    style = TabBarStyle.Standard,
                    size = TabSize.Small
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTabBar_standard_small_light.png")
    }

    @Test
    fun tabBar_standard_large_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsTabBar(
                    tabs = sampleTabs,
                    selectedIndex = 2,
                    onTabSelected = {},
                    style = TabBarStyle.Standard,
                    size = TabSize.Large
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTabBar_standard_large_light.png")
    }

    @Test
    fun tabBar_compact_medium_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsTabBar(
                    tabs = sampleTabs,
                    selectedIndex = 1,
                    onTabSelected = {},
                    style = TabBarStyle.Compact,
                    size = TabSize.Medium
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTabBar_compact_medium_light.png")
    }

    @Test
    fun tabBar_compact_medium_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsTabBar(
                    tabs = sampleTabs,
                    selectedIndex = 0,
                    onTabSelected = {},
                    style = TabBarStyle.Compact,
                    size = TabSize.Medium
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTabBar_compact_medium_dark.png")
    }

    @Test
    fun tabBar_compact_small_withBadge_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsTabBar(
                    tabs = listOf(
                        TabItem("Home", Icons.Default.Home),
                        TabItem("Search", Icons.Default.Search, badgeCount = 5),
                        TabItem("Settings", Icons.Default.Settings, badgeCount = 99)
                    ),
                    selectedIndex = 1,
                    onTabSelected = {},
                    style = TabBarStyle.Compact,
                    size = TabSize.Small
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTabBar_compact_small_withBadge_light.png")
    }

    @Test
    fun tabBar_text_medium_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsTabBar(
                    tabs = listOf(
                        TabItem("Overview"),
                        TabItem("Details"),
                        TabItem("Settings")
                    ),
                    selectedIndex = 0,
                    onTabSelected = {},
                    style = TabBarStyle.Text,
                    size = TabSize.Medium
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTabBar_text_medium_light.png")
    }

    @Test
    fun tabBar_text_medium_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsTabBar(
                    tabs = listOf(
                        TabItem("Overview"),
                        TabItem("Details"),
                        TabItem("Settings")
                    ),
                    selectedIndex = 2,
                    onTabSelected = {},
                    style = TabBarStyle.Text,
                    size = TabSize.Medium
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTabBar_text_medium_dark.png")
    }

    @Test
    fun tabBar_text_large_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsTabBar(
                    tabs = listOf(
                        TabItem("Overview"),
                        TabItem("Details"),
                        TabItem("Settings")
                    ),
                    selectedIndex = 1,
                    onTabSelected = {},
                    style = TabBarStyle.Text,
                    size = TabSize.Large
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTabBar_text_large_light.png")
    }

    @Test
    fun tabBar_scrollable_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsTabBar(
                    tabs = listOf(
                        TabItem("Tab 1", Icons.Default.Home),
                        TabItem("Tab 2", Icons.Default.Search),
                        TabItem("Tab 3", Icons.Default.Settings),
                        TabItem("Tab 4", Icons.Default.Home),
                        TabItem("Tab 5", Icons.Default.Search)
                    ),
                    selectedIndex = 2,
                    onTabSelected = {},
                    style = TabBarStyle.Standard,
                    size = TabSize.Medium,
                    scrollable = true
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTabBar_scrollable_light.png")
    }

    @Test
    fun tabBar_scrollable_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsTabBar(
                    tabs = listOf(
                        TabItem("Tab 1", Icons.Default.Home),
                        TabItem("Tab 2", Icons.Default.Search),
                        TabItem("Tab 3", Icons.Default.Settings),
                        TabItem("Tab 4", Icons.Default.Home),
                        TabItem("Tab 5", Icons.Default.Search)
                    ),
                    selectedIndex = 3,
                    onTabSelected = {},
                    style = TabBarStyle.Standard,
                    size = TabSize.Medium,
                    scrollable = true
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTabBar_scrollable_dark.png")
    }
}
