package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

// Brutalist CrumbsFilterBar — 34dp horizontal row per handoff Screen 5 +
// layouts-pages line 31. Layout: [count cell (accent fill)] [divider] [chips]
// [Spacer.weight] [divider] [sort slot]. State is fully hoisted; the caller
// owns selection and selection updates.

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
    count: Int,
    chips: ImmutableList<FilterChipItem>,
    selectedChipIds: Set<String>,
    onChipToggled: (String) -> Unit,
    sortLabel: String,
    onSortClick: () -> Unit,
    mode: FilterMode = FilterMode.Single,
    modifier: Modifier = Modifier
) {
    val colors = LocalCrumbsColors.current
    val stroke = LocalCrumbsStroke.current
    val typography = LocalCrumbsTypography.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(colors.surface)
            .testTag("filter-bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Accent count cell
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .background(colors.accent)
                .padding(horizontal = 10.dp)
                .testTag("filter-bar-count"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "%03d".format(count),
                style = typography.captionMono,
                color = colors.onAccent,
            )
        }
        VerticalInkDivider(stroke.hairline, colors.ink)
        // Chip row, centered slot
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            chips.forEach { chip ->
                val selected = chip.id in selectedChipIds
                Box(
                    modifier = Modifier
                        .border(
                            stroke.hairline,
                            if (selected) colors.accent else colors.ink,
                            RectangleShape,
                        )
                        .background(if (selected) colors.accent else Color.Transparent)
                        .semantics(mergeDescendants = true) {
                            role = Role.Checkbox
                            toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
                            contentDescription = chip.label
                        }
                        .minimumInteractiveComponentSize()
                        .clickable {
                            when (mode) {
                                FilterMode.Single -> onChipToggled(chip.id)
                                FilterMode.Multi -> onChipToggled(chip.id)
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .testTag("filter-bar-chip-${chip.id}"),
                ) {
                    Text(
                        text = chip.label.uppercase(),
                        style = typography.metaMono,
                        color = if (selected) colors.onAccent else colors.ink,
                    )
                }
            }
        }
        VerticalInkDivider(stroke.hairline, colors.ink)
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
                text = sortLabel.uppercase(),
                style = typography.captionMono,
                color = colors.ink,
            )
        }
    }
}

@Composable
private fun VerticalInkDivider(width: androidx.compose.ui.unit.Dp, color: Color) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(color),
    )
}

@Preview(name = "Filter Bar Light", showBackground = true)
@Composable
private fun PreviewFilterBarLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsFilterBar(
            count = 42,
            chips = persistentListOf(
                FilterChipItem("text", "Text"),
                FilterChipItem("image", "Image"),
                FilterChipItem("link", "Link"),
            ),
            selectedChipIds = setOf("text"),
            onChipToggled = {},
            sortLabel = "Newest",
            onSortClick = {},
        )
    }
}

@Preview(name = "Filter Bar Dark", showBackground = true)
@Composable
private fun PreviewFilterBarDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsFilterBar(
            count = 12,
            chips = persistentListOf(
                FilterChipItem("text", "Text"),
                FilterChipItem("image", "Image"),
            ),
            selectedChipIds = emptySet(),
            onChipToggled = {},
            sortLabel = "A→Z",
            onSortClick = {},
        )
    }
}
