package com.github.jayteealao.crumbs.designsystem.modifiers

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Brutalist ink-stroked strikethrough overlay. `TextDecoration.LineThrough`
 * cannot control stroke weight or cap; these modifiers draw a chunky
 * square-capped line so the strike reads as part of the brutalist contract
 * rather than a Material affordance.
 *
 * Two variants:
 *  - [brutalistStrikethrough] draws ONE line across the vertical centre of the
 *    node. It serves single-line or two-line headlines (the Reddit card title).
 *  - [brutalistStrikethroughLines] draws one line PER LAID-OUT TEXT LINE from
 *    the node's [TextLayoutResult]. It serves multi-line bodies (the Twitter
 *    card preview), where a single centre line would cross only one line.
 */
fun Modifier.brutalistStrikethrough(
    active: Boolean,
    color: Color = Color.Black,
    strokeWidthDp: Dp = 2.dp,
): Modifier =
    this.drawWithContent {
        drawContent()
        if (active) {
            val y = size.height / 2f
            drawLine(
                color = color,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = strokeWidthDp.toPx(),
                cap = StrokeCap.Square,
            )
        }
    }

/**
 * Per-line strikethrough for a multi-line `Text`. The caller captures the
 * node's [TextLayoutResult] through `onTextLayout` and hands it back through
 * [layout]; the lambda is read only inside the draw phase, so a layout change
 * invalidates draw and not composition.
 *
 * Each strike is anchored on that line's baseline, `fontSizePx * 0.3f` above
 * it, which sits through the x-height of a mono face. It is NOT anchored on
 * the line box centre: the default `LineHeightStyle` trims the first line's
 * box, so a box-centre anchor would land at a different glyph height on line
 * 0 than on the lines below it. The loop runs over `lineCount`, the lines the
 * text actually laid out, never over `maxLines`.
 */
fun Modifier.brutalistStrikethroughLines(
    active: Boolean,
    layout: () -> TextLayoutResult?,
    fontSizePx: Float,
    color: Color = Color.Black,
    strokeWidthDp: Dp = 2.dp,
): Modifier =
    this.drawWithContent {
        drawContent()
        if (!active) return@drawWithContent
        val result = layout() ?: return@drawWithContent
        val strokeWidth = strokeWidthDp.toPx()
        for (line in 0 until result.lineCount) {
            val y = result.getLineBaseline(line) - fontSizePx * 0.3f
            drawLine(
                color = color,
                start = Offset(result.getLineLeft(line), y),
                end = Offset(result.getLineRight(line), y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Square,
            )
        }
    }
