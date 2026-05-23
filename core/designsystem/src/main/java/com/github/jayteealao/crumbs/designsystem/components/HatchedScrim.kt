package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import kotlin.math.hypot

// Brutalist 45° hatched scrim — flat black at `baseAlpha` with diagonal
// stripes drawn on top. Pure Compose Canvas (no android.graphics.Bitmap
// dependency) so it renders deterministically under Roborazzi NATIVE mode.
// Consumed by OverlayShell as the modal backdrop per
// handoff-layouts-pages.jsx:86-88,:102.

@Composable
fun HatchedScrim(
    modifier: Modifier = Modifier,
    baseAlpha: Float = 0.32f,
    hatchColor: Color = Color.Black.copy(alpha = 0.08f),
    hatchSpacing: Dp = 8.dp,
    hatchAngle: Float = 45f,
) {
    Canvas(modifier = modifier) {
        drawRect(color = Color.Black.copy(alpha = baseAlpha))
        val spacingPx = hatchSpacing.toPx()
        val strokePx = 1.dp.toPx()
        val diagonal = hypot(size.width, size.height)
        rotate(degrees = hatchAngle) {
            var x = -diagonal
            while (x <= diagonal) {
                drawLine(
                    color = hatchColor,
                    start = Offset(x, -diagonal),
                    end = Offset(x, diagonal),
                    strokeWidth = strokePx,
                )
                x += spacingPx
            }
        }
    }
}

@Preview(name = "HatchedScrim Light", showBackground = true)
@Composable
private fun PreviewHatchedScrimLight() {
    CrumbsTheme(darkTheme = false) {
        HatchedScrim(modifier = Modifier.fillMaxSize())
    }
}

@Preview(name = "HatchedScrim Dark", showBackground = true)
@Composable
private fun PreviewHatchedScrimDark() {
    CrumbsTheme(darkTheme = true) {
        HatchedScrim(modifier = Modifier.fillMaxSize())
    }
}
