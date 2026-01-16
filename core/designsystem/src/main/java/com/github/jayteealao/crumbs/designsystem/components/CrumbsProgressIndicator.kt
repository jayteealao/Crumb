package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors

/**
 * Crumbs progress indicator with theme colors
 *
 * Features:
 * - Two styles: Circular (spinning) and Linear (progress bar)
 * - Three sizes: Small (24dp), Medium (40dp), Large (56dp)
 * - Indeterminate (spinning) and determinate (progress value) modes
 * - Theme-aware accent color
 *
 * Component variants for testing:
 * - Style: Circular/Linear
 * - Size: Small/Medium/Large
 * - Mode: Indeterminate/Determinate
 */

enum class ProgressSize {
    Small,      // 24.dp
    Medium,     // 40.dp
    Large       // 56.dp
}

enum class ProgressStyle {
    Circular,
    Linear
}

@Composable
fun CrumbsProgressIndicator(
    modifier: Modifier = Modifier,
    size: ProgressSize = ProgressSize.Medium,
    style: ProgressStyle = ProgressStyle.Circular,
    progress: Float? = null,  // null = indeterminate, 0-1 = determinate
    color: Color? = null
) {
    val colors = LocalCrumbsColors.current
    val indicatorColor = color ?: colors.accent

    val diameter = when (size) {
        ProgressSize.Small -> 24.dp
        ProgressSize.Medium -> 40.dp
        ProgressSize.Large -> 56.dp
    }

    val strokeWidth = when (size) {
        ProgressSize.Small -> 2.dp
        ProgressSize.Medium -> 3.dp
        ProgressSize.Large -> 4.dp
    }

    when (style) {
        ProgressStyle.Circular -> {
            if (progress != null) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = modifier.size(diameter),
                    color = indicatorColor,
                    strokeWidth = strokeWidth,
                    trackColor = colors.surfaceVariant
                )
            } else {
                CircularProgressIndicator(
                    modifier = modifier.size(diameter),
                    color = indicatorColor,
                    strokeWidth = strokeWidth,
                    trackColor = colors.surfaceVariant
                )
            }
        }

        ProgressStyle.Linear -> {
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = modifier.width(diameter * 4),
                    color = indicatorColor,
                    trackColor = colors.surfaceVariant
                )
            } else {
                LinearProgressIndicator(
                    modifier = modifier.width(diameter * 4),
                    color = indicatorColor,
                    trackColor = colors.surfaceVariant
                )
            }
        }
    }
}

// Previews
@Preview(name = "Circular Medium Indeterminate Light", showBackground = true)
@Composable
private fun PreviewProgressCircularMediumIndeterminateLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsProgressIndicator(
            style = ProgressStyle.Circular,
            size = ProgressSize.Medium
        )
    }
}

@Preview(name = "Circular Medium Indeterminate Dark", showBackground = true)
@Composable
private fun PreviewProgressCircularMediumIndeterminateDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsProgressIndicator(
            style = ProgressStyle.Circular,
            size = ProgressSize.Medium
        )
    }
}

@Preview(name = "Circular Small Light", showBackground = true)
@Composable
private fun PreviewProgressCircularSmallLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsProgressIndicator(
            style = ProgressStyle.Circular,
            size = ProgressSize.Small
        )
    }
}

@Preview(name = "Circular Large Light", showBackground = true)
@Composable
private fun PreviewProgressCircularLargeLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsProgressIndicator(
            style = ProgressStyle.Circular,
            size = ProgressSize.Large
        )
    }
}

@Preview(name = "Circular Medium Determinate Light", showBackground = true)
@Composable
private fun PreviewProgressCircularMediumDeterminateLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsProgressIndicator(
            style = ProgressStyle.Circular,
            size = ProgressSize.Medium,
            progress = 0.65f
        )
    }
}

@Preview(name = "Linear Medium Indeterminate Light", showBackground = true)
@Composable
private fun PreviewProgressLinearMediumIndeterminateLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsProgressIndicator(
            style = ProgressStyle.Linear,
            size = ProgressSize.Medium
        )
    }
}

@Preview(name = "Linear Medium Determinate Light", showBackground = true)
@Composable
private fun PreviewProgressLinearMediumDeterminateLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsProgressIndicator(
            style = ProgressStyle.Linear,
            size = ProgressSize.Medium,
            progress = 0.75f
        )
    }
}
