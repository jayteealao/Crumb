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
class FilterOverlayTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val typeSections = persistentListOf(
        FilterOverlaySection(
            "Type",
            persistentListOf(
                FilterChipItem("all", "ALL"),
                FilterChipItem("article", "ARTICLES"),
                FilterChipItem("video", "VIDEOS"),
                FilterChipItem("thread", "THREADS"),
            ),
        ),
    )

    private val multiSections = persistentListOf(
        FilterOverlaySection(
            "Type",
            persistentListOf(
                FilterChipItem("all", "ALL"),
                FilterChipItem("article", "ARTICLES"),
                FilterChipItem("video", "VIDEOS"),
                FilterChipItem("thread", "THREADS"),
            ),
        ),
        FilterOverlaySection(
            "Tags",
            persistentListOf(
                FilterChipItem("android", "ANDROID"),
                FilterChipItem("compose", "COMPOSE"),
                FilterChipItem("kotlin", "KOTLIN"),
                FilterChipItem("ux", "UX"),
            ),
        ),
    )

    /** M-06: default state — no chips selected, filter type = ALL (all unselected). */
    @Test
    fun filterOverlay_default() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                FilterOverlay(
                    visible = true,
                    sections = typeSections,
                    selectedChipIds = emptySet(),
                    onChipToggled = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/FilterOverlay_default.png")
    }

    /** M-06: tags section active — Type=ARTICLES and two tag chips selected. */
    @Test
    fun filterOverlay_withTagsSelected() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                FilterOverlay(
                    visible = true,
                    sections = multiSections,
                    selectedChipIds = setOf("article", "android", "compose"),
                    onChipToggled = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/FilterOverlay_withTagsSelected.png")
    }

    @Test
    fun filterOverlay_visible_withSelection_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                FilterOverlay(
                    visible = true,
                    sections = typeSections,
                    selectedChipIds = setOf("article"),
                    onChipToggled = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/FilterOverlay_visible_withSelection_light.png")
    }

    @Test
    fun filterOverlay_visible_noSelection_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                FilterOverlay(
                    visible = true,
                    sections = typeSections,
                    selectedChipIds = emptySet(),
                    onChipToggled = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/FilterOverlay_visible_noSelection_light.png")
    }

    @Test
    fun filterOverlay_visible_withSelection_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                FilterOverlay(
                    visible = true,
                    sections = typeSections,
                    selectedChipIds = setOf("article", "video"),
                    onChipToggled = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/FilterOverlay_visible_withSelection_dark.png")
    }

    /**
     * AC6: overlay rendering both the Type and Tags sections with one chip active in each — light.
     * Uses the [multiSections] fixture's bare chip ids; FilterOverlay is namespace-agnostic (the
     * `tag:` prefix is HomeRoute's concern), so the selected ids match the fixture ids directly.
     */
    @Test
    fun filterOverlay_typeAndTags_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                FilterOverlay(
                    visible = true,
                    sections = multiSections,
                    selectedChipIds = setOf("article", "android"),
                    onChipToggled = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/FilterOverlay_typeAndTags_light.png")
    }

    /** AC6: overlay rendering both the Type and Tags sections with one chip active in each — dark. */
    @Test
    fun filterOverlay_typeAndTags_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                FilterOverlay(
                    visible = true,
                    sections = multiSections,
                    selectedChipIds = setOf("article", "android"),
                    onChipToggled = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/FilterOverlay_typeAndTags_dark.png")
    }
}
