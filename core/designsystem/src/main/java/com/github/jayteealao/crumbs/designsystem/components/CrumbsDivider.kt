package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors

/**
 * Crumbs divider for visual separation
 *
 * Features:
 * - Three styles: Solid (flat line), Dashed, Dotted
 * - Three thicknesses: Thin (0.5dp), Regular (1dp), Thick (2dp)
 * - Horizontal and vertical orientations
 * - Optional start/end indentation
 * - Theme-aware colors
 *
 * Component variants for testing:
 * - Style: Solid/Dashed/Dotted
 * - Thickness: Thin/Regular/Thick
 * - Orientation: Horizontal/Vertical
 * - With/without indentation
 */

enum class DividerStyle {
    Solid,
    Dashed,
    Dotted
}

enum class DividerThickness {
    Thin,       // 0.5.dp
    Regular,    // 1.dp
    Thick       // 2.dp
}

@Composable
fun CrumbsDivider(
    modifier: Modifier = Modifier,
    style: DividerStyle = DividerStyle.Solid,
    thickness: DividerThickness = DividerThickness.Regular,
    color: Color? = null,
    startIndent: Dp = 0.dp,
    endIndent: Dp = 0.dp,
    horizontal: Boolean = true
) {
    val colors = LocalCrumbsColors.current
    val dividerColor = color ?: colors.surfaceVariant

    val strokeWidth = when (thickness) {
        DividerThickness.Thin -> 0.5.dp
        DividerThickness.Regular -> 1.dp
        DividerThickness.Thick -> 2.dp
    }

    val pathEffect = when (style) {
        DividerStyle.Solid -> null
        DividerStyle.Dashed -> PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        DividerStyle.Dotted -> PathEffect.dashPathEffect(floatArrayOf(2f, 4f), 0f)
    }

    if (horizontal) {
        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(strokeWidth)
                .padding(start = startIndent, end = endIndent)
        ) {
            drawLine(
                color = dividerColor,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = strokeWidth.toPx(),
                pathEffect = pathEffect
            )
        }
    } else {
        Canvas(
            modifier = modifier
                .fillMaxHeight()
                .width(strokeWidth)
                .padding(top = startIndent, bottom = endIndent)
        ) {
            drawLine(
                color = dividerColor,
                start = Offset(size.width / 2, 0f),
                end = Offset(size.width / 2, size.height),
                strokeWidth = strokeWidth.toPx(),
                pathEffect = pathEffect
            )
        }
    }
}

// Previews
@Preview(name = "Solid Regular Horizontal Light", showBackground = true)
@Composable
private fun PreviewDividerSolidRegularHorizontalLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsDivider(
            style = DividerStyle.Solid,
            thickness = DividerThickness.Regular
        )
    }
}

@Preview(name = "Solid Regular Horizontal Dark", showBackground = true)
@Composable
private fun PreviewDividerSolidRegularHorizontalDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsDivider(
            style = DividerStyle.Solid,
            thickness = DividerThickness.Regular
        )
    }
}

@Preview(name = "Dashed Thin Light", showBackground = true)
@Composable
private fun PreviewDividerDashedThinLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsDivider(
            style = DividerStyle.Dashed,
            thickness = DividerThickness.Thin
        )
    }
}

@Preview(name = "Dotted Regular Light", showBackground = true)
@Composable
private fun PreviewDividerDottedRegularLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsDivider(
            style = DividerStyle.Dotted,
            thickness = DividerThickness.Regular
        )
    }
}

@Preview(name = "Solid Thick Light", showBackground = true)
@Composable
private fun PreviewDividerSolidThickLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsDivider(
            style = DividerStyle.Solid,
            thickness = DividerThickness.Thick
        )
    }
}
