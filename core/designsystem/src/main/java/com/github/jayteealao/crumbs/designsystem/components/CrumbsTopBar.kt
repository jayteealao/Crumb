package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

// Brutalist CrumbsTopBar — handoff-components.jsx:418-453. Three rows tall
// (~88dp total): optional kicker row (metaMono, onSurfaceVariant) over the
// wordmark/action row (alignment Alignment.Bottom) over a 1.5dp ink bottom
// border. Background uses colors.background (paper/linen) per the spec.
//
// Search mode swaps the wordmark+action row for CrumbsSearchField. The
// `trailing` action is a TopBarAction sealed-class — Search by default — and
// rendered as a 44dp square button whose bg() varies by variant.
//
// Caller provides the kicker string verbatim (including the leading "↳ "
// glyph if desired) so this component carries no copy decisions.

@Composable
fun CrumbsTopBar(
    modifier: Modifier = Modifier,
    kickerText: String? = null,
    wordmark: String = "crumbs",
    trailing: TopBarAction = TopBarAction.Search,
    onTrailingClick: () -> Unit = {},
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    isSearchActive: Boolean = false,
    onSearchActiveChange: (Boolean) -> Unit = {},
    resultsCount: Int? = null,
    resultsMillis: Int? = null,
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val stroke = LocalCrumbsStroke.current
    val typography = LocalCrumbsTypography.current
    val trailingInteraction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .testTag("top-bar"),
    ) {
        if (kickerText != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = spacing.lg, end = spacing.lg, top = spacing.sm),
            ) {
                Text(
                    text = kickerText.uppercase(),
                    style = typography.metaMono,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.testTag("top-bar-kicker"),
                )
            }
        }

        AnimatedVisibility(visible = !isSearchActive) {
            // Wordmark row — Alignment.Bottom (handoff-components.jsx:134),
            // baseline-aligned padding (top 6 / bottom 10 / start 16 / end 12).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = spacing.lg, end = spacing.md, top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = wordmark,
                        style = typography.displayHeadline,
                        color = colors.ink,
                        modifier = Modifier.testTag("top-bar-wordmark"),
                    )
                    // Inline accent dot — ink box with accent "·" glyph,
                    // baseline-aligned to the wordmark.
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .offset(x = 3.dp)
                            .background(colors.ink)
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = "•",
                            color = colors.accent,
                            fontSize = 17.sp,
                            style = typography.displayHeadline.copy(fontSize = 17.sp),
                        )
                    }
                }
                // 44dp square trailing action.
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(trailing.bg(colors))
                        .border(stroke.regular, colors.ink)
                        .clickable(
                            interactionSource = trailingInteraction,
                            indication = null,
                        ) {
                            if (trailing == TopBarAction.Search) onSearchActiveChange(true)
                            onTrailingClick()
                        }
                        .testTag("top-bar-trailing-${trailing::class.simpleName?.lowercase()}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = trailing.icon,
                        contentDescription = trailing.label,
                        tint = colors.ink,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        AnimatedVisibility(visible = isSearchActive) {
            Box(modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm)) {
                CrumbsSearchField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onBack = {
                        onSearchActiveChange(false)
                        onSearchQueryChange("")
                    },
                    resultsCount = resultsCount,
                    resultsMillis = resultsMillis,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stroke.regular)
                .background(colors.ink),
        )
    }
}

@Preview(name = "TopBar Default Light", showBackground = true)
@Composable
private fun PreviewTopBarDefaultLight() {
    CrumbsTheme(darkTheme = false) { CrumbsTopBar(kickerText = "↳ personal index / v.1.1") }
}

@Preview(name = "TopBar Default Dark", showBackground = true)
@Composable
private fun PreviewTopBarDefaultDark() {
    CrumbsTheme(darkTheme = true) { CrumbsTopBar(kickerText = "↳ personal index / v.1.1") }
}

@Preview(name = "TopBar Search Active", showBackground = true)
@Composable
private fun PreviewTopBarSearch() {
    CrumbsTheme(darkTheme = false) {
        CrumbsTopBar(isSearchActive = true, searchQuery = "design patterns", resultsCount = 4, resultsMillis = 12)
    }
}

@Preview(name = "TopBar SwitchToFeed", showBackground = true)
@Composable
private fun PreviewTopBarSwitchToFeed() {
    CrumbsTheme(darkTheme = false) {
        CrumbsTopBar(kickerText = "↳ GRAPH", trailing = TopBarAction.SwitchToFeed)
    }
}

@Preview(name = "TopBar Close (Sub-screen)", showBackground = true)
@Composable
private fun PreviewTopBarClose() {
    CrumbsTheme(darkTheme = false) {
        CrumbsTopBar(kickerText = "↳ settings", trailing = TopBarAction.Close)
    }
}
