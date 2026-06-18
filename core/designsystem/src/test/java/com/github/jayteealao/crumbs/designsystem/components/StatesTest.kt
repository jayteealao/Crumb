package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
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

// EmptyState screenshot tests. LoadingCard tests moved to LoadingCardTest
// (uses pinned scanLinePositionFraction to avoid infinite-transition hangs).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StatesTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

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
                    actionText = "Add first bookmark",
                    onActionClick = {},
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
                    actionText = "Add first bookmark",
                    onActionClick = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/EmptyState_withAction_dark.png")
    }
}
