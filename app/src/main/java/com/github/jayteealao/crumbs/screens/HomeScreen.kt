package com.github.jayteealao.crumbs.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.github.jayteealao.crumbs.data.BannerState
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.designsystem.components.BottomNavTab
import com.github.jayteealao.crumbs.designsystem.components.CrumbsBanner
import com.github.jayteealao.crumbs.designsystem.components.CrumbsBottomNav
import com.github.jayteealao.crumbs.designsystem.components.CrumbsFilterBar
import com.github.jayteealao.crumbs.designsystem.components.CrumbsSnackbar
import com.github.jayteealao.crumbs.designsystem.components.CrumbsTopBar
import com.github.jayteealao.crumbs.designsystem.components.FilterChipItem
import com.github.jayteealao.crumbs.designsystem.components.FilterMode
import com.github.jayteealao.crumbs.designsystem.layouts.HomeScaffold
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@androidx.compose.runtime.Immutable
data class HomeUiState(
    val selectedTab: BottomNavTab = BottomNavTab.TWITTER,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val filterCount: Int = 0,
    val selectedFilterChipIds: Set<String> = emptySet(),
    val bannerState: BannerState? = null,
)

internal val HomeFilterChips: ImmutableList<FilterChipItem> = persistentListOf(
    FilterChipItem("all", "ALL"),
    FilterChipItem("article", "ARTICLES"),
    FilterChipItem("video", "VIDEOS"),
    FilterChipItem("image", "IMAGES"),
    FilterChipItem("thread", "THREADS"),
    FilterChipItem("text", "TEXT"),
)

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onTabSelected: (BottomNavTab) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onChipToggled: (String) -> Unit,
    onSortClick: () -> Unit,
    onBannerCta: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    tabContent: @Composable (BottomNavTab, PaddingValues) -> Unit,
) {
    HomeScaffold(
        modifier = modifier.testTag("home-screen"),
        topBar = {
            CrumbsTopBar(
                kickerText = "CRUMBS",
                wordmark = "crumbs•",
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                isSearchActive = uiState.isSearchActive,
                onSearchActiveChange = onSearchActiveChange,
            )
        },
        banner = uiState.bannerState?.let { state ->
            {
                AnimatedVisibility(visible = true) {
                    CrumbsBanner(
                        kickerLine = state.kicker,
                        detail = state.detail,
                        ctaLabel = state.ctaLabel,
                        onCta = onBannerCta,
                    )
                }
            }
        },
        filterBar = {
            CrumbsFilterBar(
                count = uiState.filterCount,
                chips = HomeFilterChips,
                selectedChipIds = uiState.selectedFilterChipIds,
                onChipToggled = onChipToggled,
                sortLabel = "RECENT",
                onSortClick = onSortClick,
                mode = FilterMode.Single,
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                CrumbsSnackbar(
                    message = data.visuals.message,
                    actionLabel = data.visuals.actionLabel,
                    onAction = { data.performAction() },
                    modifier = Modifier.testTag("snackbar"),
                )
            }
        },
        bottomBar = {
            CrumbsBottomNav(
                selectedTab = uiState.selectedTab,
                onTabSelected = onTabSelected,
            )
        },
    ) { padding ->
        tabContent(uiState.selectedTab, padding)
    }
}

@Preview(name = "Home Twitter Light", showBackground = true)
@Composable
private fun PreviewHomeLight() {
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
        ) { _, _ -> }
    }
}

@Preview(name = "Home All Dark", showBackground = true)
@Composable
private fun PreviewHomeDark() {
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
        ) { _, _ -> }
    }
}

@Preview(name = "Home With Banner Light", showBackground = true)
@Composable
private fun PreviewHomeBannerLight() {
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
        ) { _, _ -> }
    }
}
