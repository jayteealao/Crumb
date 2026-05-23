package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke

// Brutalist LoadingCard — sharp-edged skeleton blocks (no shimmer) + a single
// horizontal scan-line that animates top-to-bottom on infinite loop. Skeleton
// blocks are static; the scan-line is the only animated element.
//
// Tests pass `scanLinePositionFraction` (a constant) to pin a frame — avoids
// the infinite-transition Roborazzi hang documented in issue #413.

@Composable
fun LoadingCard(
    hasImage: Boolean = true,
    modifier: Modifier = Modifier,
    scanLinePositionFraction: Float? = null,
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val stroke = LocalCrumbsStroke.current
    val shapes = LocalCrumbsShapes.current

    val transition = rememberInfiniteTransition(label = "loading-card")
    val animatedFraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scan-line-fraction",
    )
    val fraction = scanLinePositionFraction ?: animatedFraction

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .border(stroke.regular, colors.ink, shapes.card)
            .testTag("loading-card")
            .drawBehind {
                val y = size.height * fraction
                drawLine(
                    color = colors.ink,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = stroke.hairline.toPx(),
                )
            },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (hasImage) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(colors.ink.copy(alpha = 0.08f))
                        .testTag("loading-card-skeleton"),
                )
            }
            Column(
                modifier = Modifier.padding(spacing.md),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(20.dp)
                        .background(colors.ink.copy(alpha = 0.08f)),
                )
                Spacer(modifier = Modifier.height(spacing.sm))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .height(12.dp)
                        .background(colors.ink.copy(alpha = 0.08f)),
                )
                Spacer(modifier = Modifier.height(spacing.xs))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(12.dp)
                        .background(colors.ink.copy(alpha = 0.08f)),
                )
                Spacer(modifier = Modifier.height(spacing.xs))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(12.dp)
                        .background(colors.ink.copy(alpha = 0.08f)),
                )
            }
        }
    }
}

@Preview(name = "With Image Light", showBackground = true)
@Composable
private fun PreviewLoadingCardWithImage() {
    CrumbsTheme(darkTheme = false) {
        LoadingCard(hasImage = true, scanLinePositionFraction = 0.5f)
    }
}

@Preview(name = "Text Only Dark", showBackground = true)
@Composable
private fun PreviewLoadingCardTextOnlyDark() {
    CrumbsTheme(darkTheme = true) {
        LoadingCard(hasImage = false, scanLinePositionFraction = 0.5f)
    }
}
