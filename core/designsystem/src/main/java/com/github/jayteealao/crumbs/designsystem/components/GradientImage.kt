package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

/**
 * Crumbs gradient image component
 *
 * Features:
 * - Image display with optional gradient overlay
 * - Six gradient directions: Top/Bottom/Left/Right/Diagonal
 * - Three gradient intensities: Light (0.3), Medium (0.5), Dark (0.7)
 * - Customizable gradient color (defaults to black)
 * - Cut-corner shape (bottom-end) by default
 * - Optional overlay content for text/buttons
 * - Coil image loading with error handling
 * - Theme-aware colors
 *
 * Component variants for testing:
 * - Gradient direction: 6 directions
 * - Gradient intensity: Light/Medium/Dark
 * - With/without gradient
 * - With/without overlay content
 */

enum class GradientDirection {
    TopToBottom,
    BottomToTop,
    LeftToRight,
    RightToLeft,
    DiagonalTLBR,    // Top-left to bottom-right
    DiagonalBLTR     // Bottom-left to top-right
}

enum class GradientIntensity(val alpha: Float) {
    Light(0.3f),
    Medium(0.5f),
    Dark(0.7f)
}

@Composable
fun GradientImage(
    imageUrl: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    shape: Shape = CrumbsShapes.card,
    gradientColor: Color = Color.Black,
    gradientDirection: GradientDirection = GradientDirection.BottomToTop,
    gradientIntensity: GradientIntensity = GradientIntensity.Medium,
    overlayContent: @Composable (BoxScope.() -> Unit)? = null
) {
    val colors = LocalCrumbsColors.current

    Surface(
        modifier = modifier,
        shape = shape,
        color = colors.surfaceVariant
    ) {
        Box {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        val gradient = when (gradientDirection) {
                            GradientDirection.TopToBottom -> Brush.verticalGradient(
                                colors = listOf(
                                    gradientColor.copy(alpha = gradientIntensity.alpha),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = size.height * 0.5f
                            )

                            GradientDirection.BottomToTop -> Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    gradientColor.copy(alpha = gradientIntensity.alpha)
                                ),
                                startY = size.height * 0.5f,
                                endY = size.height
                            )

                            GradientDirection.LeftToRight -> Brush.horizontalGradient(
                                colors = listOf(
                                    gradientColor.copy(alpha = gradientIntensity.alpha),
                                    Color.Transparent
                                ),
                                startX = 0f,
                                endX = size.width * 0.5f
                            )

                            GradientDirection.RightToLeft -> Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    gradientColor.copy(alpha = gradientIntensity.alpha)
                                ),
                                startX = size.width * 0.5f,
                                endX = size.width
                            )

                            GradientDirection.DiagonalTLBR -> Brush.linearGradient(
                                colors = listOf(
                                    gradientColor.copy(alpha = gradientIntensity.alpha),
                                    Color.Transparent
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, size.height)
                            )

                            GradientDirection.DiagonalBLTR -> Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    gradientColor.copy(alpha = gradientIntensity.alpha)
                                ),
                                start = Offset(0f, size.height),
                                end = Offset(size.width, 0f)
                            )
                        }
                        drawRect(brush = gradient)
                    },
                contentScale = contentScale
            )

            overlayContent?.invoke(this)
        }
    }
}

// Previews
@Preview(name = "BottomToTop Medium Light", showBackground = true)
@Composable
private fun PreviewGradientImageBottomToTopMediumLight() {
    CrumbsTheme(darkTheme = false) {
        GradientImage(
            imageUrl = "https://via.placeholder.com/800x450",
            gradientDirection = GradientDirection.BottomToTop,
            gradientIntensity = GradientIntensity.Medium,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(name = "BottomToTop Medium Dark", showBackground = true)
@Composable
private fun PreviewGradientImageBottomToTopMediumDark() {
    CrumbsTheme(darkTheme = true) {
        GradientImage(
            imageUrl = "https://via.placeholder.com/800x450",
            gradientDirection = GradientDirection.BottomToTop,
            gradientIntensity = GradientIntensity.Medium,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(name = "TopToBottom Dark Light", showBackground = true)
@Composable
private fun PreviewGradientImageTopToBottomDarkLight() {
    CrumbsTheme(darkTheme = false) {
        GradientImage(
            imageUrl = "https://via.placeholder.com/800x450",
            gradientDirection = GradientDirection.TopToBottom,
            gradientIntensity = GradientIntensity.Dark,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(name = "LeftToRight Light Light", showBackground = true)
@Composable
private fun PreviewGradientImageLeftToRightLightLight() {
    CrumbsTheme(darkTheme = false) {
        GradientImage(
            imageUrl = "https://via.placeholder.com/800x450",
            gradientDirection = GradientDirection.LeftToRight,
            gradientIntensity = GradientIntensity.Light,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(name = "DiagonalTLBR Medium Light", showBackground = true)
@Composable
private fun PreviewGradientImageDiagonalTLBRMediumLight() {
    CrumbsTheme(darkTheme = false) {
        GradientImage(
            imageUrl = "https://via.placeholder.com/800x450",
            gradientDirection = GradientDirection.DiagonalTLBR,
            gradientIntensity = GradientIntensity.Medium,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(name = "With Overlay Content Light", showBackground = true)
@Composable
private fun PreviewGradientImageWithOverlayLight() {
    CrumbsTheme(darkTheme = false) {
        val typography = LocalCrumbsTypography.current
        val colors = LocalCrumbsColors.current
        GradientImage(
            imageUrl = "https://via.placeholder.com/800x450",
            gradientDirection = GradientDirection.BottomToTop,
            gradientIntensity = GradientIntensity.Dark,
            modifier = Modifier.fillMaxSize(),
            overlayContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Text(
                        text = "Overlay Text",
                        style = typography.titleLarge,
                        color = Color.White
                    )
                }
            }
        )
    }
}
