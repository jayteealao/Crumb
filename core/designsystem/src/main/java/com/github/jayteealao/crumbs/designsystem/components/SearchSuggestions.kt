package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

/**
 * Search suggestions dropdown component
 *
 * Shows below the top bar when search is active:
 * - Recent searches (from history)
 * - Suggested searches (future: based on tags, frequent terms)
 *
 * Note: Full search infrastructure doesn't exist yet, so this component
 * is designed to accept data as parameters and will integrate with
 * SearchViewModel when it's implemented.
 *
 * @param recentSearches List of recent search queries
 * @param suggestions List of suggested searches (future feature)
 * @param onSuggestionClick Callback when a suggestion is clicked
 * @param modifier Modifier to be applied to the component
 */
@Composable
fun SearchSuggestions(
    recentSearches: List<String>,
    suggestions: List<String> = emptyList(),
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val typography = LocalCrumbsTypography.current

    Surface(
        modifier = modifier,
        shape = CrumbsShapes.card,
        color = colors.surface.copy(alpha = 0.95f),
        tonalElevation = 4.dp
    ) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            // Recent searches section
            if (recentSearches.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent",
                        style = typography.labelMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(
                            horizontal = spacing.lg,
                            vertical = spacing.sm
                        )
                    )
                }

                items(recentSearches.take(5)) { search ->
                    SearchHistoryItem(
                        text = search,
                        onClick = { onSuggestionClick(search) }
                    )
                }
            }

            // Suggestions section (future feature)
            if (suggestions.isNotEmpty()) {
                item {
                    Text(
                        text = "Suggestions",
                        style = typography.labelMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(
                            horizontal = spacing.lg,
                            vertical = spacing.sm
                        )
                    )
                }

                items(suggestions.take(5)) { suggestion ->
                    SearchSuggestionItem(
                        text = suggestion,
                        onClick = { onSuggestionClick(suggestion) }
                    )
                }
            }

            // Empty state when no suggestions
            if (recentSearches.isEmpty() && suggestions.isEmpty()) {
                item {
                    Text(
                        text = "Start typing to search...",
                        style = typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(spacing.lg)
                    )
                }
            }
        }
    }
}

/**
 * Individual recent search history item
 */
@Composable
private fun SearchHistoryItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val typography = LocalCrumbsTypography.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            tint = colors.textSecondary
        )

        Text(
            text = text,
            style = typography.bodyMedium,
            color = colors.textPrimary
        )
    }
}

/**
 * Individual search suggestion item (future feature)
 */
@Composable
private fun SearchSuggestionItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val typography = LocalCrumbsTypography.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = colors.accent
        )

        Text(
            text = text,
            style = typography.bodyMedium,
            color = colors.textPrimary
        )
    }
}

// Previews
@Preview(name = "With Recent Searches", showBackground = true)
@Composable
private fun PreviewSearchSuggestionsWithRecent() {
    CrumbsTheme(darkTheme = false) {
        SearchSuggestions(
            recentSearches = listOf(
                "design patterns",
                "kotlin coroutines",
                "compose animation"
            ),
            onSuggestionClick = {}
        )
    }
}

@Preview(name = "Empty State", showBackground = true)
@Composable
private fun PreviewSearchSuggestionsEmpty() {
    CrumbsTheme(darkTheme = false) {
        SearchSuggestions(
            recentSearches = emptyList(),
            onSuggestionClick = {}
        )
    }
}

@Preview(name = "With Suggestions", showBackground = true)
@Composable
private fun PreviewSearchSuggestionsWithAll() {
    CrumbsTheme(darkTheme = false) {
        SearchSuggestions(
            recentSearches = listOf("kotlin", "compose"),
            suggestions = listOf("android", "jetpack", "material design"),
            onSuggestionClick = {}
        )
    }
}

@Preview(name = "Dark Theme", showBackground = true)
@Composable
private fun PreviewSearchSuggestionsDark() {
    CrumbsTheme(darkTheme = true) {
        SearchSuggestions(
            recentSearches = listOf(
                "design patterns",
                "kotlin coroutines"
            ),
            onSuggestionClick = {}
        )
    }
}
