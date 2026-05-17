package com.github.jayteealao.crumbs.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.github.jayteealao.crumbs.designsystem.components.BottomNavTab
import com.github.jayteealao.crumbs.designsystem.components.CrumbsBottomNav
import com.github.jayteealao.crumbs.designsystem.components.CrumbsFilterBar
import com.github.jayteealao.crumbs.designsystem.components.CrumbsTopBar
import com.github.jayteealao.crumbs.designsystem.components.FilterChipItem
import com.github.jayteealao.crumbs.designsystem.components.FilterMode
import com.github.jayteealao.crumbs.designsystem.layouts.HomeScaffold
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import kotlinx.collections.immutable.persistentListOf

@androidx.compose.runtime.Immutable
data class HomeUiState(
    val selectedTab: BottomNavTab = BottomNavTab.TWITTER,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val filterCount: Int = 0,
)

private val HomeFilterChips = persistentListOf(
    FilterChipItem("all", "ALL"),
    FilterChipItem("articles", "ARTICLES"),
    FilterChipItem("videos", "VIDEOS"),
)

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onTabSelected: (BottomNavTab) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
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
        filterBar = {
            CrumbsFilterBar(
                count = uiState.filterCount,
                chips = HomeFilterChips,
                selectedChipIds = emptySet(),
                onChipToggled = { /* TODO behaviors slice */ },
                sortLabel = "RECENT",
                onSortClick = { /* TODO behaviors slice */ },
                mode = FilterMode.Single,
            )
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
        ) { _, _ -> }
    }
}
