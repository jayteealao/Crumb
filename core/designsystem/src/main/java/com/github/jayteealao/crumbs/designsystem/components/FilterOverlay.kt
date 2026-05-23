package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.layouts.OverlayShell
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

// FilterOverlay — OverlayShell-mounted picker for Type / Tags / Collection
// filter chips. Replaces the in-bar chip row from the original components
// slice per handoff-components.jsx (single FILTER: <label> cell + dedicated
// overlay). Active chips render ink-bg / accent-fg via CrumbsFilterChipActive
// with a × dismiss span.
//
// Cross-slice note: OverlayShell currently positions as a bottom sheet (per
// the original layouts slice). When fidelity-layouts re-fits OverlayShell to
// a floating card, this picker inherits the new positioning unchanged.

@androidx.compose.runtime.Immutable
data class FilterOverlaySection(
    val title: String,
    val chips: ImmutableList<FilterChipItem>,
)

@Composable
fun FilterOverlay(
    visible: Boolean,
    sections: ImmutableList<FilterOverlaySection>,
    selectedChipIds: Set<String>,
    onChipToggled: (String) -> Unit,
    onDismiss: () -> Unit,
    onApply: () -> Unit = onDismiss,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val stroke = LocalCrumbsStroke.current
    val typography = LocalCrumbsTypography.current

    OverlayShell(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
        header = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "FILTERS",
                    style = typography.captionMono,
                    color = colors.ink,
                    modifier = Modifier.testTag("filter-overlay-title"),
                )
            }
        },
        footer = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .border(stroke.hairline, colors.ink)
                    .padding(spacing.md)
                    .clickable { onApply() }
                    .testTag("filter-overlay-apply"),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "APPLY",
                    style = typography.captionMono,
                    color = colors.accent,
                )
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("filter-overlay"),
            contentPadding = PaddingValues(spacing.md),
        ) {
            sections.forEach { section ->
                item {
                    Text(
                        text = section.title.uppercase(),
                        style = typography.metaMono,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = spacing.xs),
                    )
                }
                items(section.chips) { chip ->
                    val selected = chip.id in selectedChipIds
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                            ) { onChipToggled(chip.id) }
                            .testTag("filter-overlay-chip-${chip.id}"),
                    ) {
                        if (selected) {
                            CrumbsFilterChipActive(
                                label = chip.label,
                                onDismiss = { onChipToggled(chip.id) },
                            )
                        } else {
                            Text(
                                text = chip.label.uppercase(),
                                style = typography.bodyMono,
                                color = colors.ink,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(spacing.sm)) }
            }
        }
    }
}

@Preview(name = "FilterOverlay Light", showBackground = true)
@Composable
private fun PreviewFilterOverlayLight() {
    CrumbsTheme(darkTheme = false) {
        FilterOverlay(
            visible = true,
            sections = persistentListOf(
                FilterOverlaySection(
                    "Type",
                    persistentListOf(
                        FilterChipItem("all", "ALL"),
                        FilterChipItem("article", "ARTICLES"),
                        FilterChipItem("video", "VIDEOS"),
                    ),
                ),
            ),
            selectedChipIds = setOf("article"),
            onChipToggled = {},
            onDismiss = {},
        )
    }
}
