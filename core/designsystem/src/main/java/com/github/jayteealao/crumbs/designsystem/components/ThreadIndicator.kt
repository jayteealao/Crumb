package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

/**
 * Thread indicator component for Twitter threads
 *
 * Shows "+ N more tweets" to indicate thread continuation.
 * Clickable to expand thread inline (future feature).
 *
 * @param threadCount Total number of tweets in thread
 * @param onClick Callback when indicator is clicked (future: expand thread)
 * @param modifier Modifier to be applied to the component
 */
@Composable
fun ThreadIndicator(
    threadCount: Int,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val typography = LocalCrumbsTypography.current

    // Show "+ N more" where N = threadCount - 1 (excluding current tweet)
    val remainingCount = (threadCount - 1).coerceAtLeast(0)

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = CrumbsShapes.chip, // top-start cut (4dp)
        color = colors.accent.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, colors.accent)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = spacing.md,
                vertical = spacing.sm
            ),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Forum,
                contentDescription = "Thread",
                tint = colors.accent,
                modifier = Modifier.size(16.dp)
            )

            Text(
                text = "+ $remainingCount more",
                style = typography.labelMedium,
                color = colors.accent
            )
        }
    }
}

// Previews
@Preview(name = "Thread Indicator - 2 tweets", showBackground = true)
@Composable
private fun PreviewThreadIndicatorSmall() {
    CrumbsTheme(darkTheme = false) {
        ThreadIndicator(threadCount = 2)
    }
}

@Preview(name = "Thread Indicator - 5 tweets", showBackground = true)
@Composable
private fun PreviewThreadIndicatorMedium() {
    CrumbsTheme(darkTheme = false) {
        ThreadIndicator(threadCount = 5)
    }
}

@Preview(name = "Thread Indicator - 20 tweets", showBackground = true)
@Composable
private fun PreviewThreadIndicatorLarge() {
    CrumbsTheme(darkTheme = false) {
        ThreadIndicator(threadCount = 20)
    }
}

@Preview(name = "Thread Indicator - Dark", showBackground = true)
@Composable
private fun PreviewThreadIndicatorDark() {
    CrumbsTheme(darkTheme = true) {
        ThreadIndicator(threadCount = 5)
    }
}
