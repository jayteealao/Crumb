package com.github.jayteealao.crumbs.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.dropbox.differ.SimpleImageComparator
import com.github.jayteealao.crumbs.data.BannerState
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.designsystem.components.BottomNavTab
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val options = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(
            imageComparator = SimpleImageComparator(maxDistance = 0.01f),
        ),
    )

    @Test
    fun homeScreen_twitter_light() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                HomeScreen(
                    uiState = HomeUiState(selectedTab = BottomNavTab.TWITTER, filterCount = 42),
                    onTabSelected = {},
                    onSearchQueryChange = {},
                    onSearchActiveChange = {},
                    onChipToggled = {},
                    onSortClick = {},
                    onBannerCta = {},
                    snackbarHostState = SnackbarHostState(),
                ) { _, _ ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(LocalCrumbsColors.current.surface),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/HomeScreen_twitter_light.png", options)
    }

    @Test
    fun homeScreen_all_dark() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                HomeScreen(
                    uiState = HomeUiState(selectedTab = BottomNavTab.ALL, filterCount = 13),
                    onTabSelected = {},
                    onSearchQueryChange = {},
                    onSearchActiveChange = {},
                    onChipToggled = {},
                    onSortClick = {},
                    onBannerCta = {},
                    snackbarHostState = SnackbarHostState(),
                ) { _, _ ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(LocalCrumbsColors.current.surface),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/HomeScreen_all_dark.png", options)
    }

    @Test
    fun homeScreen_withSyncErrorBanner_light() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                HomeScreen(
                    uiState = HomeUiState(
                        selectedTab = BottomNavTab.TWITTER,
                        bannerState = BannerState(
                            source = BookmarkSource.Twitter,
                            kicker = "ERR · RECONNECT TWITTER",
                            detail = "Twitter session expired. Tap to reconnect.",
                            ctaLabel = "RECONNECT",
                        ),
                    ),
                    onTabSelected = {},
                    onSearchQueryChange = {},
                    onSearchActiveChange = {},
                    onChipToggled = {},
                    onSortClick = {},
                    onBannerCta = {},
                    snackbarHostState = SnackbarHostState(),
                ) { _, _ ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(LocalCrumbsColors.current.surface),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/HomeScreen_withSyncErrorBanner_light.png", options)
    }

    @Test
    fun homeScreen_bannerCta_click_invokesCallback() {
        // The banner CTA is the only escape from a broken sync state. Maestro
        // exercises the tap visually but does not assert the callback fires —
        // this Compose test pins the contract that clicking banner-cta routes
        // through to onBannerCta so the OAuth intent can be dispatched.
        var ctaFired = false
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                HomeScreen(
                    uiState = HomeUiState(
                        selectedTab = BottomNavTab.TWITTER,
                        bannerState = BannerState(
                            source = BookmarkSource.Twitter,
                            kicker = "ERR · RECONNECT TWITTER",
                            detail = "Twitter session expired. Tap to reconnect.",
                            ctaLabel = "RECONNECT",
                        ),
                    ),
                    onTabSelected = {},
                    onSearchQueryChange = {},
                    onSearchActiveChange = {},
                    onChipToggled = {},
                    onSortClick = {},
                    onBannerCta = { ctaFired = true },
                    snackbarHostState = SnackbarHostState(),
                ) { _, _ ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(LocalCrumbsColors.current.surface),
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("banner-cta").performClick()

        assertTrue(
            "Tapping banner-cta must invoke onBannerCta — this is the OAuth re-entry path",
            ctaFired,
        )
    }

    @Test
    fun homeScreen_withSyncErrorBanner_dark() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                HomeScreen(
                    uiState = HomeUiState(
                        selectedTab = BottomNavTab.REDDIT,
                        bannerState = BannerState(
                            source = BookmarkSource.Reddit,
                            kicker = "ERR · RECONNECT REDDIT",
                            detail = "Reddit session expired. Tap to reconnect.",
                            ctaLabel = "RECONNECT",
                        ),
                    ),
                    onTabSelected = {},
                    onSearchQueryChange = {},
                    onSearchActiveChange = {},
                    onChipToggled = {},
                    onSortClick = {},
                    onBannerCta = {},
                    snackbarHostState = SnackbarHostState(),
                ) { _, _ ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(LocalCrumbsColors.current.surface),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/HomeScreen_withSyncErrorBanner_dark.png", options)
    }
}
