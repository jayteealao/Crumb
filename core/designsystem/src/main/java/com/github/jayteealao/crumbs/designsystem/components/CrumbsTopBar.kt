package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

// Brutalist CrumbsTopBar — Material3 TopAppBar + TextField stripped.
// Two-row layout: mono kicker (10sp uppercase) over 56dp wordmark/search row,
// terminated by a 1.5dp ink bottom border. ~88dp total height. Search expands
// in-place via AnimatedVisibility — no scrollBehavior collapsing (brutalist
// chrome stays static).
//
// @param kickerText  Optional mono kicker row above wordmark (e.g. "↳ 2026-05-17").
// @param wordmark    Brand wordmark text (default "crumbs•").
// @param searchQuery Active search text.
// @param onSearchQueryChange Search input callback.
// @param isSearchActive Whether the search row is expanded.
// @param onSearchActiveChange Callback toggling the active state.

@Composable
fun CrumbsTopBar(
    modifier: Modifier = Modifier,
    kickerText: String? = null,
    wordmark: String = "crumbs•",
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    isSearchActive: Boolean = false,
    onSearchActiveChange: (Boolean) -> Unit = {},
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val stroke = LocalCrumbsStroke.current
    val typography = LocalCrumbsTypography.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
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
                    style = typography.captionMono,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.testTag("top-bar-kicker"),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AnimatedVisibility(visible = !isSearchActive) {
                Text(
                    text = wordmark,
                    style = typography.displayHeadline,
                    color = colors.ink,
                    modifier = Modifier
                        .testTag("top-bar-wordmark")
                        .clickable { /* no-op; wordmark is brand, not interactive */ },
                )
            }
            AnimatedVisibility(visible = isSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("top-bar-search"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close search",
                        tint = colors.ink,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                onSearchActiveChange(false)
                                onSearchQueryChange("")
                            },
                    )
                    Spacer(Modifier.size(spacing.sm))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        textStyle = typography.bodyMono.copy(color = colors.ink),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        singleLine = true,
                        modifier = Modifier
                            .testTag("top-bar-search-field"),
                    )
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { onSearchQueryChange("") },
                        )
                    }
                }
            }
            AnimatedVisibility(visible = !isSearchActive) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = colors.ink,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onSearchActiveChange(true) },
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

// Previews
@Preview(name = "Normal Expanded Light", showBackground = true)
@Composable
private fun PreviewTopBarNormalLight() {
    CrumbsTheme(darkTheme = false) { CrumbsTopBar(kickerText = "↳ 2026-05-17") }
}

@Preview(name = "Normal Expanded Dark", showBackground = true)
@Composable
private fun PreviewTopBarNormalDark() {
    CrumbsTheme(darkTheme = true) { CrumbsTopBar(kickerText = "↳ 2026-05-17") }
}

@Preview(name = "Search Active Empty", showBackground = true)
@Composable
private fun PreviewTopBarSearchEmpty() {
    CrumbsTheme(darkTheme = false) {
        CrumbsTopBar(isSearchActive = true, searchQuery = "")
    }
}

@Preview(name = "Search Active With Query", showBackground = true)
@Composable
private fun PreviewTopBarSearchWithQuery() {
    CrumbsTheme(darkTheme = false) {
        CrumbsTopBar(isSearchActive = true, searchQuery = "design patterns")
    }
}
