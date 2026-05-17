package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

// Brutalist GradientImage — Surface wrapper stripped. Box-bordered AsyncImage
// with a sibling Box overlay applying the gradient via drawWithContent. Coil
// 2.x defaults to native image size when no Modifier.size(...) is set, so
// callers MUST constrain dimensions (via Modifier.size, fillMaxSize, or
// aspectRatio) to avoid pulling full-resolution remote images into memory.

enum class GradientDirection {
    TopToBottom,
    BottomToTop,
    LeftToRight,
    RightToLeft,
    DiagonalTLBR,
    DiagonalBLTR,
}

enum class GradientIntensity(val alpha: Float) {
    Light(0.3f),
    Medium(0.5f),
    Dark(0.7f),
}

@Composable
fun GradientImage(
    imageUrl: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    gradientColor: Color = Color.Black,
    gradientDirection: GradientDirection = GradientDirection.BottomToTop,
    gradientIntensity: GradientIntensity = GradientIntensity.Medium,
    overlayContent: @Composable (BoxScope.() -> Unit)? = null
) {
    val colors = LocalCrumbsColors.current
    val stroke = LocalCrumbsStroke.current

    Box(
        modifier = modifier
            .border(stroke.hairline, colors.ink, RectangleShape)
            .testTag("gradient-image"),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    val gradient = when (gradientDirection) {
                        GradientDirection.TopToBottom -> Brush.verticalGradient(
                            colors = listOf(gradientColor.copy(alpha = gradientIntensity.alpha), Color.Transparent),
                            startY = 0f,
                            endY = size.height * 0.5f,
                        )
                        GradientDirection.BottomToTop -> Brush.verticalGradient(
                            colors = listOf(Color.Transparent, gradientColor.copy(alpha = gradientIntensity.alpha)),
                            startY = size.height * 0.5f,
                            endY = size.height,
                        )
                        GradientDirection.LeftToRight -> Brush.horizontalGradient(
                            colors = listOf(gradientColor.copy(alpha = gradientIntensity.alpha), Color.Transparent),
                            startX = 0f,
                            endX = size.width * 0.5f,
                        )
                        GradientDirection.RightToLeft -> Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, gradientColor.copy(alpha = gradientIntensity.alpha)),
                            startX = size.width * 0.5f,
                            endX = size.width,
                        )
                        GradientDirection.DiagonalTLBR -> Brush.linearGradient(
                            colors = listOf(gradientColor.copy(alpha = gradientIntensity.alpha), Color.Transparent),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height),
                        )
                        GradientDirection.DiagonalBLTR -> Brush.linearGradient(
                            colors = listOf(Color.Transparent, gradientColor.copy(alpha = gradientIntensity.alpha)),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, 0f),
                        )
                    }
                    drawRect(brush = gradient)
                },
            contentScale = contentScale,
        )
        overlayContent?.invoke(this)
    }
}

@Preview(name = "BottomToTop Medium Light", showBackground = true)
@Composable
private fun PreviewGradientImageBottomToTopMediumLight() {
    CrumbsTheme(darkTheme = false) {
        GradientImage(
            imageUrl = "https://via.placeholder.com/800x450",
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(name = "BottomToTop Medium Dark", showBackground = true)
@Composable
private fun PreviewGradientImageBottomToTopMediumDark() {
    CrumbsTheme(darkTheme = true) {
        GradientImage(
            imageUrl = "https://via.placeholder.com/800x450",
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(name = "With Overlay Light", showBackground = true)
@Composable
private fun PreviewGradientImageWithOverlayLight() {
    CrumbsTheme(darkTheme = false) {
        val typography = LocalCrumbsTypography.current
        GradientImage(
            imageUrl = "https://via.placeholder.com/800x450",
            gradientIntensity = GradientIntensity.Dark,
            modifier = Modifier.fillMaxSize(),
            overlayContent = {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Text("OVERLAY TEXT", style = typography.captionMono, color = Color.White)
                }
            },
        )
    }
}
