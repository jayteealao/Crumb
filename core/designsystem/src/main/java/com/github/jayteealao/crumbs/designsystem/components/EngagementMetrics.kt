package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

/**
 * Crumbs engagement metrics display component
 *
 * Features:
 * - Displays social engagement stats (likes, replies, reposts, shares, etc.)
 * - Two layouts: Horizontal (side-by-side) and Vertical (stacked)
 * - Dense and normal spacing modes
 * - Optional click actions for each metric
 * - Theme-aware accent colors for interactive metrics
 *
 * Component variants for testing:
 * - Layout: Horizontal/Vertical
 * - Density: Dense/Normal
 * - Interactive/Display-only
 * - Variable count of metrics
 */

enum class MetricsLayout {
    Horizontal,
    Vertical
}

data class MetricValue(
    val icon: ImageVector,
    val count: Int,
    val contentDescription: String,
    val onClick: (() -> Unit)? = null
)

@Composable
fun EngagementMetrics(
    metrics: List<MetricValue>,
    modifier: Modifier = Modifier,
    layout: MetricsLayout = MetricsLayout.Horizontal,
    dense: Boolean = false
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val typography = LocalCrumbsTypography.current

    val itemSpacing = if (dense) spacing.md else spacing.lg
    val iconSize = if (dense) 18.dp else 20.dp
    val textStyle = if (dense) typography.caption else typography.labelMedium

    when (layout) {
        MetricsLayout.Horizontal -> {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                metrics.forEach { metric ->
                    MetricItem(
                        metric = metric,
                        iconSize = iconSize,
                        textStyle = textStyle,
                        colors = colors,
                        spacing = spacing
                    )
                }
            }
        }

        MetricsLayout.Vertical -> {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(itemSpacing)
            ) {
                metrics.forEach { metric ->
                    MetricItem(
                        metric = metric,
                        iconSize = iconSize,
                        textStyle = textStyle,
                        colors = colors,
                        spacing = spacing
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricItem(
    metric: MetricValue,
    iconSize: androidx.compose.ui.unit.Dp,
    textStyle: androidx.compose.ui.text.TextStyle,
    colors: com.github.jayteealao.crumbs.designsystem.theme.CrumbsColors,
    spacing: com.github.jayteealao.crumbs.designsystem.theme.CrumbsSpacing
) {
    val baseModifier = if (metric.onClick != null) {
        Modifier.clickable { metric.onClick.invoke() }
    } else Modifier

    Row(
        modifier = baseModifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = metric.icon,
            contentDescription = metric.contentDescription,
            modifier = Modifier.size(iconSize),
            tint = if (metric.onClick != null) colors.accent else colors.textSecondary
        )
        Text(
            text = formatCount(metric.count),
            style = textStyle,
            color = colors.textSecondary
        )
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
        count >= 1_000 -> "%.1fK".format(count / 1_000.0)
        else -> count.toString()
    }
}

// Previews
@Preview(name = "Horizontal Normal Light", showBackground = true)
@Composable
private fun PreviewEngagementMetricsHorizontalNormalLight() {
    CrumbsTheme(darkTheme = false) {
        EngagementMetrics(
            metrics = listOf(
                MetricValue(Icons.Default.Favorite, 234, "Likes", onClick = {}),
                MetricValue(Icons.Default.Reply, 45, "Replies", onClick = {}),
                MetricValue(Icons.Default.Repeat, 12, "Reposts", onClick = {}),
                MetricValue(Icons.Default.Share, 8, "Shares", onClick = {})
            ),
            layout = MetricsLayout.Horizontal
        )
    }
}

@Preview(name = "Horizontal Normal Dark", showBackground = true)
@Composable
private fun PreviewEngagementMetricsHorizontalNormalDark() {
    CrumbsTheme(darkTheme = true) {
        EngagementMetrics(
            metrics = listOf(
                MetricValue(Icons.Default.Favorite, 234, "Likes", onClick = {}),
                MetricValue(Icons.Default.Reply, 45, "Replies", onClick = {}),
                MetricValue(Icons.Default.Repeat, 12, "Reposts", onClick = {}),
                MetricValue(Icons.Default.Share, 8, "Shares", onClick = {})
            ),
            layout = MetricsLayout.Horizontal
        )
    }
}

@Preview(name = "Horizontal Dense Light", showBackground = true)
@Composable
private fun PreviewEngagementMetricsHorizontalDenseLight() {
    CrumbsTheme(darkTheme = false) {
        EngagementMetrics(
            metrics = listOf(
                MetricValue(Icons.Default.Favorite, 1500, "Likes"),
                MetricValue(Icons.Default.Reply, 89, "Replies"),
                MetricValue(Icons.Default.Repeat, 234, "Reposts")
            ),
            layout = MetricsLayout.Horizontal,
            dense = true
        )
    }
}

@Preview(name = "Vertical Normal Light", showBackground = true)
@Composable
private fun PreviewEngagementMetricsVerticalNormalLight() {
    CrumbsTheme(darkTheme = false) {
        EngagementMetrics(
            metrics = listOf(
                MetricValue(Icons.Default.Favorite, 234, "Likes", onClick = {}),
                MetricValue(Icons.Default.Reply, 45, "Replies", onClick = {}),
                MetricValue(Icons.Default.Repeat, 12, "Reposts", onClick = {})
            ),
            layout = MetricsLayout.Vertical
        )
    }
}

@Preview(name = "Vertical Dense Light", showBackground = true)
@Composable
private fun PreviewEngagementMetricsVerticalDenseLight() {
    CrumbsTheme(darkTheme = false) {
        EngagementMetrics(
            metrics = listOf(
                MetricValue(Icons.Default.Favorite, 5600, "Likes"),
                MetricValue(Icons.Default.Reply, 789, "Replies"),
                MetricValue(Icons.Default.Repeat, 1200, "Reposts")
            ),
            layout = MetricsLayout.Vertical,
            dense = true
        )
    }
}
