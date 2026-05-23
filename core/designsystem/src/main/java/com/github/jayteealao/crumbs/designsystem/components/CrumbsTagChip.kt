package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.modifiers.dashedBorder
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

// Brutalist tag-chip family — three flavors matching handoff-components.jsx:
//
//   CrumbsTagChip(label, onClick)         — underline-only inline chip
//                                            (1.dp ink borderBottom + bodyMono)
//   CrumbsFilterChipActive(label, onDismiss) — ink-bg / accent-fg "active" chip
//                                            with × dismiss span (captionMono)
//   CrumbsAddTagChip(onClick)             — dashed-underline ADD TAG affordance
//                                            (0.5 alpha, captionMono)
//
// The plain TagChip is the workhorse used inside BookmarkCard's tag FlowRow.

@Composable
fun CrumbsTagChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCrumbsColors.current
    val stroke = LocalCrumbsStroke.current
    val typography = LocalCrumbsTypography.current
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onClick() }
            .testTag("tag-chip-$label"),
    ) {
        Text(
            text = "#$label",
            style = typography.bodyMono,
            color = colors.ink,
            modifier = Modifier
                .padding(end = 4.dp, bottom = 2.dp)
                .bottomBorder(stroke.hairline, colors.ink),
        )
    }
}

@Composable
fun CrumbsFilterChipActive(
    label: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCrumbsColors.current
    val stroke = LocalCrumbsStroke.current
    val typography = LocalCrumbsTypography.current
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .background(colors.ink)
            .border(stroke.regular, colors.ink)
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .testTag("filter-chip-active-$label"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = typography.captionMono,
            color = colors.accent,
        )
        Text(
            text = "×",
            style = typography.captionMono,
            color = colors.accent.copy(alpha = 0.6f),
            modifier = Modifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) { onDismiss() }
                .testTag("filter-chip-dismiss-$label"),
        )
    }
}

@Composable
fun CrumbsAddTagChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCrumbsColors.current
    val typography = LocalCrumbsTypography.current
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onClick() }
            .testTag("add-tag-chip"),
    ) {
        Text(
            text = "+ ADD TAG",
            style = typography.captionMono,
            color = colors.ink.copy(alpha = 0.5f),
            modifier = Modifier
                .padding(bottom = 2.dp)
                .dashedBorder(
                    width = 1.dp,
                    color = colors.ink.copy(alpha = 0.5f),
                    dashLengthDp = 3.dp,
                    gapDp = 2.dp,
                ),
        )
    }
}

// 1px (or strokeWidth) bottom border drawn beneath the text without affecting
// layout height. Distinct from Modifier.border() which draws on all four sides.
private fun Modifier.bottomBorder(strokeWidth: Dp, color: Color): Modifier =
    this.drawBehind {
        val px = strokeWidth.toPx()
        val y = size.height - px / 2f
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = px,
        )
    }

@Preview(name = "TagChip Light", showBackground = true)
@Composable
private fun PreviewTagChipLight() {
    CrumbsTheme(darkTheme = false) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CrumbsTagChip(label = "kotlin", onClick = {})
            CrumbsTagChip(label = "compose", onClick = {})
        }
    }
}

@Preview(name = "FilterChipActive Light", showBackground = true)
@Composable
private fun PreviewFilterChipActiveLight() {
    CrumbsTheme(darkTheme = false) {
        Row(modifier = Modifier.padding(16.dp)) {
            CrumbsFilterChipActive(label = "article", onDismiss = {})
        }
    }
}

@Preview(name = "AddTagChip Light", showBackground = true)
@Composable
private fun PreviewAddTagChipLight() {
    CrumbsTheme(darkTheme = false) {
        Row(modifier = Modifier.padding(16.dp)) {
            CrumbsAddTagChip(onClick = {})
        }
    }
}
