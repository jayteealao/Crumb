package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

// CrumbsFilterBar — 34dp horizontal row per handoff-components.jsx:468-486.
// Three cells: count (ink-bg, accent-fg, e.g. "247 SAVED") · FILTER cell
// (e.g. "FILTER: ALL" / "FILTER: 2 ACTIVE") · sort cell (e.g. "SORT ↓ NEW").
// Borders are top + bottom only (regular stroke). Background: colors.background.
//
// All cell labels are caller-formatted strings — HomeScreen builds them from
// item count, selected chip ids, and current sort, then passes them in.

// Retained for source compatibility with callers that still import FilterMode /
// FilterChipItem; both move into FilterOverlay's domain.
sealed interface FilterMode {
    data object Single : FilterMode
    data object Multi : FilterMode
}

@androidx.compose.runtime.Immutable
data class FilterChipItem(
    val id: String,
    val label: String,
)

@Composable
fun CrumbsFilterBar(
    countLabel: String,
    filterLabel: String,
    sortLabel: String,
    onFilterClick: () -> Unit,
    onSortClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCrumbsColors.current
    val stroke = LocalCrumbsStroke.current
    val typography = LocalCrumbsTypography.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .testTag("filter-bar"),
    ) {
        HorizontalInkLine(stroke.regular, colors.ink)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Count cell — ink bg, accent text.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(colors.ink)
                    .padding(horizontal = 10.dp)
                    .testTag("filter-bar-count"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = countLabel,
                    style = typography.captionMono,
                    color = colors.accent,
                )
            }
            VerticalInkLine(stroke.hairline, colors.ink)
            // FILTER cell — clickable to open FilterOverlay.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = filterLabel
                    }
                    .minimumInteractiveComponentSize()
                    .clickable { onFilterClick() }
                    .padding(horizontal = 10.dp)
                    .testTag("filter-bar-cell"),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = filterLabel,
                    style = typography.captionMono,
                    color = colors.onSurfaceVariant,
                )
            }
            VerticalInkLine(stroke.hairline, colors.ink)
            // Sort cell.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = "Sort: $sortLabel"
                    }
                    .minimumInteractiveComponentSize()
                    .clickable { onSortClick() }
                    .padding(horizontal = 10.dp)
                    .testTag("filter-bar-sort"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = sortLabel,
                    style = typography.captionMono,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        HorizontalInkLine(stroke.regular, colors.ink)
    }
}

@Composable
private fun VerticalInkLine(width: Dp, color: Color) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(color),
    )
}

@Composable
private fun HorizontalInkLine(height: Dp, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(color),
    )
}

@Preview(name = "FilterBar All Light", showBackground = true)
@Composable
private fun PreviewFilterBarAllLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsFilterBar(
            countLabel = "247 SAVED",
            filterLabel = "FILTER: ALL",
            sortLabel = "SORT ↓ NEW",
            onFilterClick = {},
            onSortClick = {},
        )
    }
}

@Preview(name = "FilterBar Active Light", showBackground = true)
@Composable
private fun PreviewFilterBarActiveLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsFilterBar(
            countLabel = "042 SAVED",
            filterLabel = "FILTER: 2 ACTIVE",
            sortLabel = "SORT ↓ A→Z",
            onFilterClick = {},
            onSortClick = {},
        )
    }
}

@Preview(name = "FilterBar All Dark", showBackground = true)
@Composable
private fun PreviewFilterBarAllDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsFilterBar(
            countLabel = "012 SAVED",
            filterLabel = "FILTER: ALL",
            sortLabel = "SORT ↓ NEW",
            onFilterClick = {},
            onSortClick = {},
        )
    }
}
