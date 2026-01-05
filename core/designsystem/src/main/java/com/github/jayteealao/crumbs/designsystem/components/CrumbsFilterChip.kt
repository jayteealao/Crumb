package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

/**
 * Filter chip style variants
 */
enum class FilterChipStyle {
    Outlined,  // Border with transparent background
    Filled     // Solid background
}

/**
 * Filter chip component for tag-based filtering UI
 *
 * Interactive chip that toggles selection state. Appears below the app bar
 * for filtering bookmarks by source, tags, date, or status.
 *
 * @param label Text to display on the chip
 * @param selected Whether the chip is currently selected
 * @param onClick Callback when the chip is clicked
 * @param leadingIcon Optional icon to show before the label
 * @param style Visual style (Outlined or Filled)
 * @param modifier Modifier to be applied to the component
 */
@Composable
fun CrumbsFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
    style: FilterChipStyle = FilterChipStyle.Outlined,
    modifier: Modifier = Modifier
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val typography = LocalCrumbsTypography.current

    // Determine colors based on style and selection state
    val backgroundColor: Color
    val borderColor: Color
    val contentColor: Color

    when (style) {
        FilterChipStyle.Outlined -> {
            backgroundColor = if (selected) colors.accent.copy(alpha = 0.2f) else Color.Transparent
            borderColor = if (selected) colors.accent else colors.textSecondary
            contentColor = if (selected) colors.accent else colors.textPrimary
        }
        FilterChipStyle.Filled -> {
            backgroundColor = if (selected) colors.accent else colors.surfaceVariant
            borderColor = Color.Transparent
            contentColor = if (selected) colors.surface else colors.textPrimary
        }
    }

    Surface(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = CrumbsShapes.chip, // top-start cut (4dp)
        color = backgroundColor,
        border = if (style == FilterChipStyle.Outlined) {
            BorderStroke(1.dp, borderColor)
        } else null
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = spacing.lg,
                vertical = spacing.md
            ),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading icon (optional)
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = contentColor
                )
            }

            // Label text
            Text(
                text = label,
                style = typography.labelLarge,
                color = contentColor
            )

            // Check icon when selected (outlined style only)
            if (selected && style == FilterChipStyle.Outlined) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    modifier = Modifier.size(16.dp),
                    tint = contentColor
                )
            }
        }
    }
}

// Previews
@Preview(name = "Outlined - Unselected", showBackground = true)
@Composable
private fun PreviewFilterChipOutlinedUnselected() {
    CrumbsTheme(darkTheme = false) {
        CrumbsFilterChip(
            label = "All Bookmarks",
            selected = false,
            onClick = {},
            style = FilterChipStyle.Outlined
        )
    }
}

@Preview(name = "Outlined - Selected", showBackground = true)
@Composable
private fun PreviewFilterChipOutlinedSelected() {
    CrumbsTheme(darkTheme = false) {
        CrumbsFilterChip(
            label = "Twitter",
            selected = true,
            onClick = {},
            style = FilterChipStyle.Outlined
        )
    }
}

@Preview(name = "Filled - Unselected", showBackground = true)
@Composable
private fun PreviewFilterChipFilledUnselected() {
    CrumbsTheme(darkTheme = false) {
        CrumbsFilterChip(
            label = "Programming",
            selected = false,
            onClick = {},
            style = FilterChipStyle.Filled
        )
    }
}

@Preview(name = "Filled - Selected", showBackground = true)
@Composable
private fun PreviewFilterChipFilledSelected() {
    CrumbsTheme(darkTheme = false) {
        CrumbsFilterChip(
            label = "Kotlin",
            selected = true,
            onClick = {},
            style = FilterChipStyle.Filled
        )
    }
}

@Preview(name = "With Icon - Outlined", showBackground = true)
@Composable
private fun PreviewFilterChipWithIcon() {
    CrumbsTheme(darkTheme = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CrumbsFilterChip(
                label = "Reddit",
                selected = false,
                onClick = {},
                leadingIcon = Icons.Default.Check,
                style = FilterChipStyle.Outlined
            )
            CrumbsFilterChip(
                label = "Twitter",
                selected = true,
                onClick = {},
                leadingIcon = Icons.Default.Check,
                style = FilterChipStyle.Outlined
            )
        }
    }
}

@Preview(name = "Dark Theme", showBackground = true)
@Composable
private fun PreviewFilterChipDark() {
    CrumbsTheme(darkTheme = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CrumbsFilterChip(
                label = "Unread",
                selected = false,
                onClick = {},
                style = FilterChipStyle.Outlined
            )
            CrumbsFilterChip(
                label = "Archived",
                selected = true,
                onClick = {},
                style = FilterChipStyle.Outlined
            )
        }
    }
}
