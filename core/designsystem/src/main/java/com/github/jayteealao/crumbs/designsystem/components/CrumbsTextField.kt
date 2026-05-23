package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

// CrumbsSearchField — the brutalist replacement for Material3 SearchBar.
// Square 36dp back button on the left, search icon, BasicTextField with a
// custom 1.5dp × 18dp block caret blinking on a 1s steps(2,end)-equivalent
// schedule, optional trailing "<N> HITS · <T>ms" counter (metaMono).
//
// API: handoff-components.jsx:597-604. `resultsCount` / `resultsMillis` use
// the JS-spec parameter names. `modifier` is a Compose convention add-on.
//
// Animation is driven by rememberInfiniteTransition with keyframes that snap
// alpha 1f at 0ms → 0f at 500ms → repeats; Restart repeatMode reproduces the
// hard on/off feel of CSS `steps(2, end)`. Tests pin a frame with
// composeRule.mainClock.autoAdvance = false to avoid Robolectric infinite-
// animation flakiness.

@Composable
fun CrumbsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    resultsCount: Int? = null,
    resultsMillis: Int? = null,
) {
    val colors = LocalCrumbsColors.current
    val stroke = LocalCrumbsStroke.current
    val typography = LocalCrumbsTypography.current
    val backInteraction = remember { MutableInteractionSource() }

    val caretAlpha by rememberInfiniteTransition(label = "caret").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0
                0f at 500
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "caret-alpha",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .testTag("search-field"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 36dp square back button.
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(colors.surface)
                .border(stroke.regular, colors.ink)
                .clickable(
                    interactionSource = backInteraction,
                    indication = null,
                ) { onBack() }
                .testTag("search-field-back"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = colors.ink,
                modifier = Modifier.size(20.dp),
            )
        }
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = colors.ink,
            modifier = Modifier.size(18.dp),
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = typography.bodyMono.copy(color = colors.ink),
                singleLine = true,
                cursorBrush = SolidColor(Color.Transparent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search-field-input"),
                decorationBox = { inner ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            inner()
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search…",
                                    style = typography.bodyMono,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.width(2.dp))
                        // Block caret: 1.5dp × 18dp ink box that snaps between
                        // alpha 1 and 0 every 500ms — visually equivalent to
                        // CSS `steps(2, end)` on the JS demo.
                        Box(
                            modifier = Modifier
                                .width(1.5.dp)
                                .height(18.dp)
                                .background(colors.ink.copy(alpha = caretAlpha))
                                .testTag("search-field-caret"),
                        )
                    }
                },
            )
        }
        if (resultsCount != null) {
            val ms = resultsMillis ?: 0
            Text(
                text = "${resultsCount} HITS · ${ms}MS",
                style = typography.metaMono,
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .fillMaxHeight(),
            )
        }
    }
}

@Preview(name = "SearchField Empty Light", showBackground = true)
@Composable
private fun PreviewSearchFieldEmptyLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsSearchField(query = "", onQueryChange = {}, onBack = {})
    }
}

@Preview(name = "SearchField With Hits Light", showBackground = true)
@Composable
private fun PreviewSearchFieldWithHitsLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsSearchField(
            query = "kotlin",
            onQueryChange = {},
            onBack = {},
            resultsCount = 4,
            resultsMillis = 12,
        )
    }
}

@Preview(name = "SearchField Empty Dark", showBackground = true)
@Composable
private fun PreviewSearchFieldEmptyDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsSearchField(query = "", onQueryChange = {}, onBack = {})
    }
}
