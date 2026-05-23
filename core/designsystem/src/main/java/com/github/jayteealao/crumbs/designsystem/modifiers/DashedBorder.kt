package com.github.jayteealao.crumbs.designsystem.modifiers

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Modifier.border() does not accept a PathEffect, so dashed borders are drawn
// manually via drawWithContent + drawPath with PathEffect.dashPathEffect.
// Used by CrumbsButton Ghost variant, CrumbsBookmarkCard footer divider, and
// CrumbsTagChip add-tag underline.
fun Modifier.dashedBorder(
    width: Dp = 1.dp,
    color: Color,
    dashLengthDp: Dp = 4.dp,
    gapDp: Dp = 3.dp,
    shape: Shape? = null,
): Modifier = this.drawWithContent {
    drawContent()
    val strokePx = width.toPx()
    val dashPx = dashLengthDp.toPx()
    val gapPx = gapDp.toPx()
    val effect = PathEffect.dashPathEffect(floatArrayOf(dashPx, gapPx), 0f)
    val path = shape?.toBorderPath(size, this) ?: Path().apply {
        addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokePx, cap = StrokeCap.Butt, pathEffect = effect),
    )
}

// Modifier.dashedDivider — single horizontal dashed line of the given height.
// Convenience wrapper used by CrumbsBookmarkCard footer.
fun Modifier.dashedDivider(
    color: Color,
    strokeWidth: Dp = 1.dp,
    dashLengthDp: Dp = 4.dp,
    gapDp: Dp = 3.dp,
): Modifier = this.drawWithContent {
    drawContent()
    val strokePx = strokeWidth.toPx()
    val dashPx = dashLengthDp.toPx()
    val gapPx = gapDp.toPx()
    val effect = PathEffect.dashPathEffect(floatArrayOf(dashPx, gapPx), 0f)
    val y = size.height / 2f
    drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = strokePx,
        pathEffect = effect,
        cap = StrokeCap.Butt,
    )
}

private fun Shape.toBorderPath(size: Size, density: Density): Path {
    val outline = createOutline(size, androidx.compose.ui.unit.LayoutDirection.Ltr, density)
    return Path().apply {
        addOutline(outline)
    }
}

private fun Path.addOutline(outline: androidx.compose.ui.graphics.Outline) {
    when (outline) {
        is androidx.compose.ui.graphics.Outline.Rectangle -> addRect(outline.rect)
        is androidx.compose.ui.graphics.Outline.Rounded -> addRoundRect(outline.roundRect)
        is androidx.compose.ui.graphics.Outline.Generic -> addPath(outline.path)
    }
}
