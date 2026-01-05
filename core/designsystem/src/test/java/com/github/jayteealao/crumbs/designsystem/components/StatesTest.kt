package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.jayteealao.crumbs.designsystem.TestCrumbsTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi screenshot tests for EmptyState and LoadingCard components
 *
 * Tests all visual states:
 * - EmptyState: default, filtered, with action button, light/dark themes
 * - LoadingCard: with image, text-only, light/dark themes
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class StatesTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // EmptyState Tests

    @Test
    fun emptyState_default_light() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                EmptyState()
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/EmptyState_default_light.png")
    }

    @Test
    fun emptyState_default_dark() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = true) {
                EmptyState()
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/EmptyState_default_dark.png")
    }

    @Test
    fun emptyState_filtered_light() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                EmptyState(
                    title = "No results found",
                    message = "No bookmarks match your search for 'design patterns'",
                    icon = Icons.Default.SearchOff
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/EmptyState_filtered_light.png")
    }

    @Test
    fun emptyState_withAction_light() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                EmptyState(
                    title = "No bookmarks yet",
                    message = "Start saving content to see it here",
                    icon = Icons.Default.BookmarkBorder,
                    actionText = "Add first bookmark",
                    onActionClick = {}
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/EmptyState_withAction_light.png")
    }

    @Test
    fun emptyState_withAction_dark() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = true) {
                EmptyState(
                    title = "No bookmarks yet",
                    message = "Start saving content to see it here",
                    icon = Icons.Default.BookmarkBorder,
                    actionText = "Add first bookmark",
                    onActionClick = {}
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/EmptyState_withAction_dark.png")
    }

    // LoadingCard Tests

    @Test
    fun loadingCard_withImage_light() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                LoadingCard(hasImage = true)
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/LoadingCard_withImage_light.png")
    }

    @Test
    fun loadingCard_withImage_dark() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = true) {
                LoadingCard(hasImage = true)
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/LoadingCard_withImage_dark.png")
    }

    @Test
    fun loadingCard_textOnly_light() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                LoadingCard(hasImage = false)
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/LoadingCard_textOnly_light.png")
    }

    @Test
    fun loadingCard_textOnly_dark() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = true) {
                LoadingCard(hasImage = false)
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/LoadingCard_textOnly_dark.png")
    }
}
