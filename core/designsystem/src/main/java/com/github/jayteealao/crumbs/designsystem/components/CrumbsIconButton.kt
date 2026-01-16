package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors

/**
 * Crumbs icon button with cut-corner styling
 *
 * Features:
 * - Four styles: Standard (transparent), Filled (solid background), FilledTonal (tonal background), Outlined (border)
 * - Three sizes: Small (36dp), Medium (40dp), Large (48dp)
 * - Cut-corner shape (top-end) matching button design
 * - Theme-aware colors and states
 *
 * Component variants for testing:
 * - Style: Standard/Filled/FilledTonal/Outlined
 * - Size: Small/Medium/Large
 * - State: Enabled/Disabled
 */

enum class IconButtonSize {
    Small,      // 36.dp
    Medium,     // 40.dp
    Large       // 48.dp
}

enum class IconButtonStyle {
    Filled,
    FilledTonal,
    Outlined,
    Standard
}

@Composable
fun CrumbsIconButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true,
    size: IconButtonSize = IconButtonSize.Medium,
    style: IconButtonStyle = IconButtonStyle.Standard,
    tint: Color? = null
) {
    val colors = LocalCrumbsColors.current

    val buttonSize = when (size) {
        IconButtonSize.Small -> 36.dp
        IconButtonSize.Medium -> 40.dp
        IconButtonSize.Large -> 48.dp
    }

    val iconTint = tint ?: when (style) {
        IconButtonStyle.Filled -> colors.surface
        IconButtonStyle.FilledTonal -> colors.accent
        IconButtonStyle.Outlined, IconButtonStyle.Standard -> colors.accent
    }

    when (style) {
        IconButtonStyle.Filled -> FilledIconButton(
            onClick = onClick,
            modifier = modifier.size(buttonSize),
            enabled = enabled,
            shape = CrumbsShapes.buttonSmall,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = colors.accent,
                contentColor = colors.surface,
                disabledContainerColor = colors.accent.copy(alpha = 0.38f),
                disabledContentColor = colors.surface.copy(alpha = 0.38f)
            )
        ) {
            icon()
        }

        IconButtonStyle.FilledTonal -> FilledTonalIconButton(
            onClick = onClick,
            modifier = modifier.size(buttonSize),
            enabled = enabled,
            shape = CrumbsShapes.buttonSmall,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = colors.accentAlpha,
                contentColor = colors.accent,
                disabledContainerColor = colors.accentAlpha.copy(alpha = 0.38f),
                disabledContentColor = colors.accent.copy(alpha = 0.38f)
            )
        ) {
            icon()
        }

        IconButtonStyle.Outlined -> OutlinedIconButton(
            onClick = onClick,
            modifier = modifier.size(buttonSize),
            enabled = enabled,
            shape = CrumbsShapes.buttonSmall,
            colors = IconButtonDefaults.outlinedIconButtonColors(
                containerColor = Color.Transparent,
                contentColor = colors.accent,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = colors.accent.copy(alpha = 0.38f)
            ),
            border = IconButtonDefaults.outlinedIconButtonBorder(enabled).copy(
                brush = androidx.compose.ui.graphics.SolidColor(
                    if (enabled) colors.accent else colors.accent.copy(alpha = 0.12f)
                )
            )
        ) {
            icon()
        }

        IconButtonStyle.Standard -> IconButton(
            onClick = onClick,
            modifier = modifier.size(buttonSize),
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = colors.accent,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = colors.accent.copy(alpha = 0.38f)
            )
        ) {
            icon()
        }
    }
}

// Previews
@Preview(name = "Filled Medium Light", showBackground = true)
@Composable
private fun PreviewIconButtonFilledMediumLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsIconButton(
            onClick = {},
            icon = { Icon(Icons.Default.Add, "Add") },
            style = IconButtonStyle.Filled,
            size = IconButtonSize.Medium
        )
    }
}

@Preview(name = "Filled Medium Dark", showBackground = true)
@Composable
private fun PreviewIconButtonFilledMediumDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsIconButton(
            onClick = {},
            icon = { Icon(Icons.Default.Add, "Add") },
            style = IconButtonStyle.Filled,
            size = IconButtonSize.Medium
        )
    }
}

@Preview(name = "FilledTonal Small Light", showBackground = true)
@Composable
private fun PreviewIconButtonFilledTonalSmallLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsIconButton(
            onClick = {},
            icon = { Icon(Icons.Default.Favorite, "Favorite") },
            style = IconButtonStyle.FilledTonal,
            size = IconButtonSize.Small
        )
    }
}

@Preview(name = "Outlined Large Light", showBackground = true)
@Composable
private fun PreviewIconButtonOutlinedLargeLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsIconButton(
            onClick = {},
            icon = { Icon(Icons.Default.Search, "Search") },
            style = IconButtonStyle.Outlined,
            size = IconButtonSize.Large
        )
    }
}

@Preview(name = "Standard Medium Disabled Light", showBackground = true)
@Composable
private fun PreviewIconButtonStandardDisabledLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsIconButton(
            onClick = {},
            icon = { Icon(Icons.Default.Add, "Add") },
            style = IconButtonStyle.Standard,
            size = IconButtonSize.Medium,
            enabled = false
        )
    }
}
