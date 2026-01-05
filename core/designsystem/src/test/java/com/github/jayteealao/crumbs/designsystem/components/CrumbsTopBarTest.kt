package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.TestCrumbsTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi screenshot tests for CrumbsTopBar component
 *
 * Tests all visual states:
 * 1. Expanded (normal) - light theme
 * 2. Expanded (normal) - dark theme
 * 3. Search active with empty query - light theme
 * 4. Search active with text - light theme
 *
 * Note: Collapsed state testing requires scroll behavior state manipulation
 * which is complex in unit tests. Collapsed state is tested manually
 * or in integration tests with actual scrolling LazyColumns.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class CrumbsTopBarTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun topBar_expanded_normal_light() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp) // Proper viewport height for visibility
                ) {
                    CrumbsTopBar(
                        isSearchActive = false
                        // scrollBehavior omitted for test simplicity
                    )
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTopBar_expanded_normal_light.png")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun topBar_expanded_normal_dark() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp) // Proper viewport height for visibility
                ) {
                    CrumbsTopBar(
                        isSearchActive = false
                        // scrollBehavior omitted for test simplicity
                    )
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTopBar_expanded_normal_dark.png")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun topBar_searchActive_emptyQuery_light() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp) // Proper viewport height for visibility
                ) {
                    CrumbsTopBar(
                        isSearchActive = true,
                        searchQuery = ""
                    )
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTopBar_searchActive_emptyQuery_light.png")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun topBar_searchActive_withQuery_light() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp) // Proper viewport height for visibility
                ) {
                    CrumbsTopBar(
                        isSearchActive = true,
                        searchQuery = "design patterns"
                    )
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTopBar_searchActive_withQuery_light.png")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun topBar_searchActive_withQuery_dark() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp) // Proper viewport height for visibility
                ) {
                    CrumbsTopBar(
                        isSearchActive = true,
                        searchQuery = "kotlin coroutines"
                    )
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTopBar_searchActive_withQuery_dark.png")
    }
}
