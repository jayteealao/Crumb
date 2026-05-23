package com.github.jayteealao.crumbs.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.components.CrumbsBookmarkCard
import com.github.jayteealao.crumbs.designsystem.components.CrumbsSearchField
import com.github.jayteealao.crumbs.designsystem.layouts.HomeScaffold
import com.github.jayteealao.crumbs.designsystem.modifiers.dashedDivider
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography
import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.models.ContentType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Stateless search surface. Per the JS handoff (handoff-layouts-pages.jsx
 * SearchScreen row + option-d-screens.jsx DSearch lines 278-393):
 *  - HomeScaffold topBar slot receives `CrumbsSearchField` directly; there is
 *    no `CrumbsTopBar` wrapper, no wordmark — this is a dedicated route, not
 *    the in-place HomeScreen search-mode swap.
 *  - bottomBar/filterBar/banner slots are intentionally empty.
 *  - Idle/empty state shows a `↳ Recent searches` header (mixed-case, not
 *    UPPERCASE) over dashed-separator query rows.
 *  - Results state shows `Showing results in title + body` over a feed of
 *    CrumbsBookmarkCard hits whose index strip is overridden to `HIT/{idx}`
 *    per the spec (option-d-screens.jsx DSearch line 290).
 */
@Composable
fun SearchScreen(
    query: String,
    uiState: SearchUiState,
    recentSearches: ImmutableList<String>,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onRecentSelected: (String) -> Unit,
    onBookmarkClick: (Bookmark) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resultsCount: Int? = (uiState as? SearchUiState.Results)?.hits?.size
    HomeScaffold(
        modifier = modifier.testTag("search-screen"),
        topBar = {
            CrumbsSearchField(
                query = query,
                onQueryChange = onQueryChange,
                onBack = onBack,
                resultsCount = resultsCount,
                resultsMillis = null,
            )
        },
        bottomBar = {},
    ) { padding ->
        SearchBody(
            uiState = uiState,
            recentSearches = recentSearches,
            onRecentSelected = { selection ->
                onRecentSelected(selection)
                onSubmit()
            },
            onBookmarkClick = onBookmarkClick,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@Composable
private fun SearchBody(
    uiState: SearchUiState,
    recentSearches: ImmutableList<String>,
    onRecentSelected: (String) -> Unit,
    onBookmarkClick: (Bookmark) -> Unit,
    modifier: Modifier = Modifier,
) {
    val typography = LocalCrumbsTypography.current
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    Column(modifier = modifier.background(colors.background)) {
        when (uiState) {
            SearchUiState.Idle -> RecentSearchesPane(
                recentSearches = recentSearches,
                onSelected = onRecentSelected,
            )
            is SearchUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.lg)
                    .testTag("search-loading"),
                contentAlignment = Alignment.TopStart,
            ) {
                Text(
                    text = "Searching…",
                    style = typography.bodyMono,
                    color = colors.onSurfaceVariant,
                )
            }
            is SearchUiState.Empty -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.lg)
                    .testTag("search-empty-state"),
            ) {
                Text(
                    text = "No results for “${uiState.query}”",
                    style = typography.bodyMono,
                    color = colors.ink,
                )
            }
            is SearchUiState.Results -> ResultsPane(
                hits = uiState.hits,
                onBookmarkClick = onBookmarkClick,
            )
        }
    }
}

@Composable
private fun RecentSearchesPane(
    recentSearches: ImmutableList<String>,
    onSelected: (String) -> Unit,
) {
    val colors = LocalCrumbsColors.current
    val typography = LocalCrumbsTypography.current
    val spacing = LocalCrumbsSpacing.current
    val stroke = LocalCrumbsStroke.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("search-recent-searches"),
    ) {
        // Mixed-case header per option-d-screens.jsx DSearch line 393 — do NOT
        // auto-uppercase. captionMono preserves the brutalist mono register.
        Text(
            text = "↳ Recent searches",
            style = typography.captionMono,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = spacing.lg,
                vertical = spacing.md,
            ),
        )
        if (recentSearches.isEmpty()) {
            Text(
                text = "Your search history is empty.",
                style = typography.bodyMono,
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = spacing.lg, vertical = spacing.sm)
                    .testTag("search-recent-empty"),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(recentSearches, key = { it }) { entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(entry) }
                            .padding(horizontal = spacing.lg, vertical = spacing.md)
                            .testTag("search-recent-item"),
                    ) {
                        Text(
                            text = entry,
                            style = typography.bodyMono,
                            color = colors.ink,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.lg)
                            .height(2.dp)
                            .dashedDivider(
                                color = colors.ink,
                                strokeWidth = stroke.hairline,
                                dashLengthDp = 4.dp,
                                gapDp = 3.dp,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultsPane(
    hits: ImmutableList<Bookmark>,
    onBookmarkClick: (Bookmark) -> Unit,
) {
    val colors = LocalCrumbsColors.current
    val typography = LocalCrumbsTypography.current
    val spacing = LocalCrumbsSpacing.current
    Column(modifier = Modifier.fillMaxSize()) {
        // Mixed-case results header per option-d-screens.jsx DSearch line 370.
        Text(
            text = "Showing results in title + body",
            style = typography.captionMono,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = spacing.lg,
                vertical = spacing.md,
            ),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("search-results-feed"),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
            contentPadding = PaddingValues(
                start = spacing.lg,
                end = spacing.lg,
                bottom = spacing.lg,
            ),
        ) {
            itemsIndexed(hits) { index, bookmark ->
                CrumbsBookmarkCard(
                    bookmark = bookmark,
                    onCardClick = { onBookmarkClick(bookmark) },
                    index = index + 1,
                    indexOverride = "HIT/%02d".format(index + 1),
                    modifier = Modifier.testTag("search-result-item"),
                )
            }
        }
    }
}

@Preview(name = "SearchScreen Recent Light", showBackground = true)
@Composable
private fun PreviewSearchRecentLight() {
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

@Preview(name = "SearchScreen Empty Dark", showBackground = true)
@Composable
private fun PreviewSearchEmptyDark() {
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

@Preview(name = "SearchScreen Results Light", showBackground = true)
@Composable
private fun PreviewSearchResultsLight() {
    val sample = Bookmark(
        id = "1",
        source = BookmarkSource.Twitter,
        author = "@compose",
        title = "Brutalist compose hits",
        previewText = "Lorem ipsum search snippet.",
        contentType = ContentType.Text,
        savedAt = 1730000000000L,
        sourceUrl = "https://example.com",
    )
    CrumbsTheme(darkTheme = false) {
        SearchScreen(
            query = "compose",
            uiState = SearchUiState.Results("compose", persistentListOf(sample)),
            recentSearches = persistentListOf(),
            onQueryChange = {},
            onSubmit = {},
            onBack = {},
            onRecentSelected = {},
            onBookmarkClick = {},
        )
    }
}
