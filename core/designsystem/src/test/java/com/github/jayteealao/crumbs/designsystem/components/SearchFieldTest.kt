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

// CrumbsSearchField's block caret is driven by an infiniteRepeatable.
// mainClock.autoAdvance = false freezes the clock so the test renders one
// deterministic frame instead of looping forever (roborazzi#413 pattern).

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SearchFieldTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun searchField_empty_light() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsSearchField(query = "", onQueryChange = {}, onBack = {})
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsSearchField_empty_light.png")
    }

    @Test
    fun searchField_empty_dark() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsSearchField(query = "", onQueryChange = {}, onBack = {})
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsSearchField_empty_dark.png")
    }

    @Test
    fun searchField_withHits_light() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsSearchField(
                    query = "kotlin",
                    onQueryChange = {},
                    onBack = {},
                    resultsCount = 4,
                    resultsMillis = 12,
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsSearchField_withHits_light.png")
    }

    @Test
    fun searchField_withHits_dark() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsSearchField(
                    query = "kotlin",
                    onQueryChange = {},
                    onBack = {},
                    resultsCount = 4,
                    resultsMillis = 12,
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsSearchField_withHits_dark.png")
    }
}
