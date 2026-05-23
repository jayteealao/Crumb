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
@Config(sdk = [34])
class FilterBarTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun filterBar_default_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsFilterBar(
                    countLabel = "042 SAVED",
                    filterLabel = "FILTER: 1 ACTIVE",
                    sortLabel = "SORT ↓ NEW",
                    onFilterClick = {},
                    onSortClick = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsFilterBar_default_light.png")
    }

    @Test
    fun filterBar_default_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsFilterBar(
                    countLabel = "042 SAVED",
                    filterLabel = "FILTER: 1 ACTIVE",
                    sortLabel = "SORT ↓ NEW",
                    onFilterClick = {},
                    onSortClick = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsFilterBar_default_dark.png")
    }

    @Test
    fun filterBar_noSelection_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsFilterBar(
                    countLabel = "012 SAVED",
                    filterLabel = "FILTER: ALL",
                    sortLabel = "SORT ↓ A→Z",
                    onFilterClick = {},
                    onSortClick = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsFilterBar_noSelection_light.png")
    }
}
