package com.github.jayteealao.crumbs.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.dropbox.differ.SimpleImageComparator
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.models.ContentType
import com.github.takahirom.roborazzi.RoborazziOptions
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
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class SearchScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val options = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(
            imageComparator = SimpleImageComparator(maxDistance = 0.01f),
        ),
    )

    private fun sampleBookmark(id: String, title: String) = Bookmark(
        id = id,
        source = BookmarkSource.Twitter,
        author = "@compose",
        title = title,
        previewText = "Preview snippet for $title with mono body text.",
        contentType = ContentType.Text,
        savedAt = 1730000000000L,
        sourceUrl = "https://example.com/$id",
    )

    @Test
    fun searchScreen_recent_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                SearchScreen(
                    query = "",
                    uiState = SearchUiState.Idle,
                    recentSearches = persistentListOf("kotlin", "compose", "brutalist"),
                    onQueryChange = {},
                    onSubmit = {},
                    onBack = {},
                    onRecentSelected = {},
                    onBookmarkClick = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/SearchScreen_recent_light.png", options)
    }

    @Test
    fun searchScreen_recent_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                SearchScreen(
                    query = "",
                    uiState = SearchUiState.Idle,
                    recentSearches = persistentListOf("kotlin", "compose", "brutalist"),
                    onQueryChange = {},
                    onSubmit = {},
                    onBack = {},
                    onRecentSelected = {},
                    onBookmarkClick = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/SearchScreen_recent_dark.png", options)
    }

    @Test
    fun searchScreen_results_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                SearchScreen(
                    query = "compose",
                    uiState = SearchUiState.Results(
                        query = "compose",
                        hits = persistentListOf(
                            sampleBookmark("1", "First hit on compose"),
                            sampleBookmark("2", "Second hit text"),
                        ),
                    ),
                    recentSearches = persistentListOf(),
                    onQueryChange = {},
                    onSubmit = {},
                    onBack = {},
                    onRecentSelected = {},
                    onBookmarkClick = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/SearchScreen_results_light.png", options)
    }

    @Test
    fun searchScreen_empty_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                SearchScreen(
                    query = "noresults",
                    uiState = SearchUiState.Empty("noresults"),
                    recentSearches = persistentListOf(),
                    onQueryChange = {},
                    onSubmit = {},
                    onBack = {},
                    onRecentSelected = {},
                    onBookmarkClick = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/SearchScreen_empty_dark.png", options)
    }
}
