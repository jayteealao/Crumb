package com.github.jayteealao.crumbs.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.github.jayteealao.crumbs.designsystem.components.FilterOverlay
import com.github.jayteealao.crumbs.designsystem.components.FilterOverlaySection
import com.github.jayteealao.crumbs.designsystem.layouts.HomeScaffold
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@androidx.compose.runtime.Immutable
data class HomeUiState(
    val selectedTab: BottomNavTab = BottomNavTab.TWITTER,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val selectedFilterChipIds: Set<String> = emptySet(),
    val bannerState: BannerState? = null,
    val itemCount: Int = 0,
) {
    /** Label shown on the filter pill in the filter bar. Computed from state, not in the UI. */
    val filterLabel: String get() {
        val ids = selectedFilterChipIds
        return when {
            ids.isEmpty() || ids == setOf("all") -> "FILTER: ALL"
            ids.size == 1 -> "FILTER: ${HomeFilterChips.firstOrNull { it.id == ids.first() }?.label ?: ids.first().uppercase()}"
            else -> "FILTER: ${ids.size} ACTIVE"
        }
    }

    /** Formatted bookmark count shown in the filter bar. Computed from state, not in the UI. */
    val countLabel: String get() = "%03d SAVED".format(itemCount)

    /** Sort pill label. Currently static; extracted here so it is testable and overridable. */
    val sortLabel: String get() = "SORT ↓ NEW"
}

internal val HomeFilterChips: ImmutableList<FilterChipItem> = persistentListOf(
    FilterChipItem("all", "ALL"),
    FilterChipItem("article", "ARTICLES"),
    FilterChipItem("video", "VIDEOS"),
    FilterChipItem("image", "IMAGES"),
    FilterChipItem("thread", "THREADS"),
    FilterChipItem("text", "TEXT"),
)

private val HomeFilterSections: ImmutableList<FilterOverlaySection> = persistentListOf(
    FilterOverlaySection(title = "Type", chips = HomeFilterChips),
)

/**
 * Root scaffold for the bookmark feed. Hosts the top bar, filter bar, bottom nav, and an
 * optional reconnect banner, then delegates the inner tab content to the [tabContent] slot.
 *
 * @param uiState Snapshot of tab selection, search state, active filter chips, and banner data.
 * @param onTabSelected Called when the user switches between Twitter, Reddit, All, or Map tabs.
 * @param onSearchQueryChange Called on every keystroke while the search bar is active.
 * @param onSearchActiveChange Called when search mode opens or closes; navigates to SearchScreen when `true`.
 * @param onChipToggled Called with the chip id when the user toggles a filter chip in the overlay.
 * @param onSortClick Called when the user taps the sort pill (handler currently deferred).
 * @param onBannerCta Called when the user taps the reconnect CTA on the service-error banner.
 * @param snackbarHostState Shared [SnackbarHostState] used to show undo-delete and sync feedback.
 * @param tabContent Slot that renders the active tab content given the selected [BottomNavTab] and scaffold padding.
 */
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
    var showFilterOverlay by rememberSaveable { mutableStateOf(false) }

    HomeScaffold(
        modifier = modifier.testTag("home-screen"),
        topBar = {
            CrumbsTopBar(
                kickerText = "↳ personal index",
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                isSearchActive = uiState.isSearchActive,
                onSearchActiveChange = onSearchActiveChange,
            )
        },
        banner = uiState.bannerState?.let { state ->
            {
                CrumbsBanner(
                    kickerLine = state.kicker,
                    detail = state.detail,
                    ctaLabel = state.ctaLabel,
                    onCta = onBannerCta,
                )
            }
        },
        filterBar = {
            CrumbsFilterBar(
                countLabel = uiState.countLabel,
                filterLabel = uiState.filterLabel,
                sortLabel = uiState.sortLabel,
                onFilterClick = { showFilterOverlay = true },
                onSortClick = onSortClick,
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
                selected = uiState.selectedTab,
                onTabSelected = onTabSelected,
            )
        },
    ) { padding ->
        tabContent(uiState.selectedTab, padding)
    }

    FilterOverlay(
        visible = showFilterOverlay,
        sections = HomeFilterSections,
        selectedChipIds = uiState.selectedFilterChipIds,
        onChipToggled = onChipToggled,
        onDismiss = { showFilterOverlay = false },
    )
}

@Preview(name = "Home Twitter Light", showBackground = true)
@Composable
private fun PreviewHomeLight() {
    CrumbsTheme(darkTheme = false) {
        HomeScreen(
            uiState = HomeUiState(selectedTab = BottomNavTab.TWITTER),
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
            uiState = HomeUiState(selectedTab = BottomNavTab.ALL),
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
