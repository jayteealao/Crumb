package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke

// Brutalist CrumbsProgressIndicator — Material3 stripped. Manual Canvas-drawn
// arc (circular) or sliding bar (linear). Indeterminate variants hoist
// progressFraction as an optional parameter so tests can pin a frame
// (avoids infinite-transition test hangs documented in roborazzi#413).

// Indeterminate-mode visible arc proportion of the full circle (0..1). 0.27
// matches the original demo's swept-arc fraction.
private const val INDETERMINATE_ARC_SWEEP_FRACTION = 0.27f

enum class ProgressSize { Small, Medium, Large }
enum class ProgressStyle { Circular, Linear }

@Composable
fun CrumbsProgressIndicator(
    modifier: Modifier = Modifier,
    size: ProgressSize = ProgressSize.Medium,
    style: ProgressStyle = ProgressStyle.Circular,
    progress: Float? = null,                  // null = indeterminate, 0..1 = determinate
    color: Color? = null,
    progressFraction: Float? = null,          // test override for indeterminate frame pinning
) {
    val colors = LocalCrumbsColors.current
    val stroke = LocalCrumbsStroke.current
    val indicatorColor = color ?: colors.accent
    val trackColor = colors.onSurfaceVariant

    val diameter = when (size) {
        ProgressSize.Small -> 24.dp
        ProgressSize.Medium -> 40.dp
        ProgressSize.Large -> 56.dp
    }
    val strokeWidth = when (size) {
        ProgressSize.Small -> 2.dp
        ProgressSize.Medium -> stroke.regular
        ProgressSize.Large -> stroke.emphasis
    }

    val animatedFraction by rememberInfiniteTransition(label = "progress").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "progress-fraction",
    )
    val fraction = progressFraction ?: animatedFraction

    when (style) {
        ProgressStyle.Circular -> {
            Canvas(modifier.size(diameter).testTag("progress-circular")) {
                val px = strokeWidth.toPx()
                val outline = Stroke(width = px, cap = StrokeCap.Square)
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(px / 2, px / 2),
                    size = Size(this.size.width - px, this.size.height - px),
                    style = outline,
                )
                val sweep = (progress?.coerceIn(0f, 1f) ?: INDETERMINATE_ARC_SWEEP_FRACTION) * 360f
                val startAngle = if (progress != null) -90f else (fraction * 360f - 90f)
                drawArc(
                    color = indicatorColor,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(px / 2, px / 2),
                    size = Size(this.size.width - px, this.size.height - px),
                    style = outline,
                )
            }
        }
        ProgressStyle.Linear -> {
            Canvas(modifier.width(diameter * 4).size(width = diameter * 4, height = strokeWidth * 2).testTag("progress-linear")) {
                val totalWidth = this.size.width
                val totalHeight = this.size.height
                drawRect(color = trackColor, size = this.size)
                if (progress != null) {
                    val w = totalWidth * progress.coerceIn(0f, 1f)
                    drawRect(color = indicatorColor, size = Size(w, totalHeight))
                } else {
                    val barWidth = totalWidth * 0.3f
                    val travel = totalWidth + barWidth
                    val x = fraction * travel - barWidth
                    drawRect(
                        color = indicatorColor,
                        topLeft = Offset(x.coerceAtLeast(0f), 0f),
                        size = Size(
                            barWidth.coerceAtMost(totalWidth - x.coerceAtLeast(0f))
                                .coerceAtLeast(0f),
                            totalHeight,
                        ),
                    )
                }
            }
        }
    }
}

// Previews
@Preview(name = "Circular Medium Indeterminate Light", showBackground = true)
@Composable
private fun PreviewProgressCircularMediumIndeterminateLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsProgressIndicator(progressFraction = 0.25f)
    }
}

@Preview(name = "Circular Medium Determinate Light", showBackground = true)
@Composable
private fun PreviewProgressCircularMediumDeterminate() {
    CrumbsTheme(darkTheme = false) {
        CrumbsProgressIndicator(progress = 0.65f)
    }
}

@Preview(name = "Linear Medium Determinate Light", showBackground = true)
@Composable
private fun PreviewProgressLinearMediumDeterminate() {
    CrumbsTheme(darkTheme = false) {
        CrumbsProgressIndicator(style = ProgressStyle.Linear, progress = 0.75f)
    }
}
