package com.github.jayteealao.crumbs.designsystem.modifiers

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Brutalist ink-stroked strikethrough overlay. `TextDecoration.LineThrough`
 * cannot control stroke weight or cap; this draws a chunky square-capped line
 * at the vertical centre of the decorated node so the strike reads as part of
 * the brutalist contract rather than a Material affordance.
 *
 * Single-line by design — see `04-plan-pending-delete.md` known limitation.
 */
fun Modifier.brutalistStrikethrough(
    active: Boolean,
    color: Color = Color.Black,
    strokeWidthDp: Dp = 2.dp,
): Modifier = this.drawWithContent {
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
