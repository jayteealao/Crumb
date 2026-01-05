package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi screenshot tests for filter and sorting components
 *
 * Tests CrumbsFilterChip, CrumbsTagChip, and CrumbsSortMenu
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class FilterComponentsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // CrumbsFilterChip Tests

    @Test
    fun filterChip_outlined_unselected_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                Surface(color = LocalCrumbsColors.current.background) {
                    Box(modifier = Modifier.padding(16.dp).wrapContentSize()) {
                        CrumbsFilterChip(
                            label = "All Bookmarks",
                            selected = false,
                            onClick = {},
                            style = FilterChipStyle.Outlined
                        )
                    }
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/FilterChip_outlined_unselected_light.png")
    }

    @Test
    fun filterChip_outlined_selected_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                Surface(color = LocalCrumbsColors.current.background) {
                    Box(modifier = Modifier.padding(16.dp).wrapContentSize()) {
                        CrumbsFilterChip(
                            label = "Twitter",
                            selected = true,
                            onClick = {},
                            style = FilterChipStyle.Outlined
                        )
                    }
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/FilterChip_outlined_selected_light.png")
    }

    @Test
    fun filterChip_filled_unselected_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                Surface(color = LocalCrumbsColors.current.background) {
                    Box(modifier = Modifier.padding(16.dp).wrapContentSize()) {
                        CrumbsFilterChip(
                            label = "Programming",
                            selected = false,
                            onClick = {},
                            style = FilterChipStyle.Filled
                        )
                    }
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/FilterChip_filled_unselected_light.png")
    }

    @Test
    fun filterChip_filled_selected_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                Surface(color = LocalCrumbsColors.current.background) {
                    Box(modifier = Modifier.padding(16.dp).wrapContentSize()) {
                        CrumbsFilterChip(
                            label = "Kotlin",
                            selected = true,
                            onClick = {},
                            style = FilterChipStyle.Filled
                        )
                    }
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/FilterChip_filled_selected_light.png")
    }

    @Test
    fun filterChip_withIcon_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                Surface(color = LocalCrumbsColors.current.background) {
                    Box(modifier = Modifier.padding(16.dp).wrapContentSize()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CrumbsFilterChip(
                                label = "Filter",
                                selected = true,
                                onClick = {},
                                leadingIcon = Icons.Default.FilterList,
                                style = FilterChipStyle.Outlined
                            )
                        }
                    }
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/FilterChip_withIcon_light.png")
    }

    @Test
    fun filterChip_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                Surface(color = LocalCrumbsColors.current.background) {
                    Box(modifier = Modifier.padding(16.dp).wrapContentSize()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CrumbsFilterChip(
                                label = "Unread",
                                selected = false,
                                onClick = {},
                                style = FilterChipStyle.Outlined
                            )
                            CrumbsFilterChip(
                                label = "Archived",
                                selected = true,
                                onClick = {},
                                style = FilterChipStyle.Outlined
                            )
                        }
                    }
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/FilterChip_dark.png")
    }

    // CrumbsTagChip Tests

    @Test
    fun tagChip_single_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                Surface(color = LocalCrumbsColors.current.background) {
                    Box(modifier = Modifier.padding(16.dp).wrapContentSize()) {
                        CrumbsTagChip(label = "programming")
                    }
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/TagChip_single_light.png")
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun tagChip_multiple_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                Surface(color = LocalCrumbsColors.current.background) {
                    Box(modifier = Modifier.padding(16.dp).wrapContentSize()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(LocalCrumbsSpacing.current.sm),
                            verticalArrangement = Arrangement.spacedBy(LocalCrumbsSpacing.current.sm)
                        ) {
                            CrumbsTagChip(label = "kotlin")
                            CrumbsTagChip(label = "compose")
                            CrumbsTagChip(label = "android")
                        }
                    }
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/TagChip_multiple_light.png")
    }

    @Test
    fun tagChip_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                Surface(color = LocalCrumbsColors.current.background) {
                    Box(modifier = Modifier.padding(16.dp).wrapContentSize()) {
                        CrumbsTagChip(label = "design patterns")
                    }
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/TagChip_dark.png")
    }

    // Note: CrumbsSortMenu uses DropdownMenu which renders in a separate popup window
    // and doesn't work well with Roborazzi screenshot tests. Visual verification is
    // done via Preview composables instead.
}
