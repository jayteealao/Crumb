package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

/**
 * Tag chip component for displaying tags on bookmark cards
 *
 * Non-interactive display-only chip with top-start cut corner.
 * Smaller and simpler than CrumbsFilterChip.
 *
 * @param label Tag text to display
 * @param modifier Modifier to be applied to the component
 */
@Composable
fun CrumbsTagChip(
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val typography = LocalCrumbsTypography.current

    Surface(
        modifier = modifier,
        shape = CrumbsShapes.chip, // top-start cut (4dp)
        color = colors.surfaceVariant,
        border = BorderStroke(1.dp, colors.textSecondary.copy(alpha = 0.5f))
    ) {
        Text(
            text = label,
            style = typography.labelMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(
                horizontal = spacing.md,
                vertical = spacing.sm
            )
        )
    }
}

// Previews
@Preview(name = "Single Tag - Light", showBackground = true)
@Composable
private fun PreviewTagChipSingle() {
    CrumbsTheme(darkTheme = false) {
        CrumbsTagChip(label = "programming")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "Multiple Tags - Light", showBackground = true)
@Composable
private fun PreviewTagChipMultiple() {
    CrumbsTheme(darkTheme = false) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(LocalCrumbsSpacing.current.sm),
            verticalArrangement = Arrangement.spacedBy(LocalCrumbsSpacing.current.sm)
        ) {
            CrumbsTagChip(label = "kotlin")
            CrumbsTagChip(label = "compose")
            CrumbsTagChip(label = "android")
        }
    }
}

@Preview(name = "Single Tag - Dark", showBackground = true)
@Composable
private fun PreviewTagChipDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsTagChip(label = "design patterns")
    }
}
