package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing

/**
 * Shimmer effect modifier for loading placeholders
 *
 * Creates an animated gradient sweep from surfaceVariant to surface
 * with smooth alpha animation.
 */
fun Modifier.shimmerEffect(): Modifier = composed {
    val colors = LocalCrumbsColors.current
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    background(colors.surfaceVariant.copy(alpha = alpha))
}

/**
 * Skeleton placeholder card shown while bookmark cards load
 *
 * Matches the structure of CrumbsBookmarkCard with shimmer animation
 * on all content areas. Provides visual feedback during data loading.
 *
 * @param hasImage Whether to show image placeholder at top
 * @param modifier Modifier to be applied to the component
 */
@Composable
fun LoadingCard(
    hasImage: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current

    Surface(
        modifier = modifier,
        shape = CrumbsShapes.card, // bottom-end cut corner (12dp)
        color = colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.textSecondary.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Image placeholder (if hasImage)
            if (hasImage) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .shimmerEffect()
                )
            }

            // Content column
            Column(
                modifier = Modifier.padding(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                // Metadata row (icon + username + timestamp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    // Source icon placeholder
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .shimmerEffect()
                    )

                    // Username placeholder
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmerEffect()
                    )

                    // Separator spacer
                    Spacer(modifier = Modifier.width(spacing.xs))

                    // Timestamp placeholder
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmerEffect()
                    )
                }

                // Title placeholder (70% width, larger height)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerEffect()
                )

                // Preview text placeholders (3-4 lines with varying widths)
                Column(
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmerEffect()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmerEffect()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmerEffect()
                    )
                }

                // Tags placeholders (2 small pill-shaped rectangles)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .shimmerEffect()
                    )
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .shimmerEffect()
                    )
                }
            }
        }
    }
}

// Previews
@Preview(name = "With Image Placeholder - Light", showBackground = true)
@Composable
private fun PreviewLoadingCardWithImage() {
    CrumbsTheme(darkTheme = false) {
        LoadingCard(hasImage = true)
    }
}

@Preview(name = "With Image Placeholder - Dark", showBackground = true)
@Composable
private fun PreviewLoadingCardWithImageDark() {
    CrumbsTheme(darkTheme = true) {
        LoadingCard(hasImage = true)
    }
}

@Preview(name = "Text Only - Light", showBackground = true)
@Composable
private fun PreviewLoadingCardTextOnly() {
    CrumbsTheme(darkTheme = false) {
        LoadingCard(hasImage = false)
    }
}

@Preview(name = "Text Only - Dark", showBackground = true)
@Composable
private fun PreviewLoadingCardTextOnlyDark() {
    CrumbsTheme(darkTheme = true) {
        LoadingCard(hasImage = false)
    }
}
