package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.collections.immutable.persistentListOf
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

    private val sampleChips = persistentListOf(
        FilterChipItem("text", "Text"),
        FilterChipItem("image", "Image"),
        FilterChipItem("link", "Link"),
    )

    @Test
    fun filterBar_default_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsFilterBar(
                    count = 42,
                    chips = sampleChips,
                    selectedChipIds = setOf("text"),
                    onChipToggled = {},
                    sortLabel = "Newest",
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
                    count = 42,
                    chips = sampleChips,
                    selectedChipIds = setOf("text"),
                    onChipToggled = {},
                    sortLabel = "Newest",
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
                    count = 12,
                    chips = sampleChips,
                    selectedChipIds = emptySet(),
                    onChipToggled = {},
                    sortLabel = "A→Z",
                    onSortClick = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsFilterBar_noSelection_light.png")
    }
}
