package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke

// Brutalist CrumbsIconButton — Material3 wrappers stripped. Single Box-based
// implementation handles all four variants via a style enum that toggles
// background fill vs ink border. Sharp corners, no ripple.

enum class IconButtonSize {
    Small,      // 36.dp
    Medium,     // 40.dp
    Large       // 48.dp
}

enum class IconButtonStyle {
    Filled,         // accent fill, no border
    FilledTonal,    // surface fill, ink border (subdued)
    Outlined,       // transparent, ink border
    Standard        // transparent, no border
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
    val stroke = LocalCrumbsStroke.current

    val square = when (size) {
        IconButtonSize.Small -> 36.dp
        IconButtonSize.Medium -> 40.dp
        IconButtonSize.Large -> 48.dp
    }

    val backgroundColor = when (style) {
        IconButtonStyle.Filled -> if (enabled) colors.accent else colors.surface
        IconButtonStyle.FilledTonal -> colors.surface
        IconButtonStyle.Outlined, IconButtonStyle.Standard -> Color.Transparent
    }
    val showBorder = style == IconButtonStyle.Outlined || style == IconButtonStyle.FilledTonal
    val resolvedTint = tint ?: when (style) {
        IconButtonStyle.Filled -> colors.onAccent
        IconButtonStyle.FilledTonal, IconButtonStyle.Outlined, IconButtonStyle.Standard -> colors.ink
    }

    var inner: Modifier = modifier
        .minimumInteractiveComponentSize()
        .size(square)
        .background(backgroundColor)
    if (showBorder) inner = inner.border(stroke.regular, colors.ink, RectangleShape)
    inner = inner
        .clickable(enabled = enabled) { onClick() }
        .testTag("icon-btn-${style.name.lowercase()}")

    Box(modifier = inner, contentAlignment = Alignment.Center) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides resolvedTint
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
            size = IconButtonSize.Medium,
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
            size = IconButtonSize.Medium,
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
            size = IconButtonSize.Large,
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
            size = IconButtonSize.Small,
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
            enabled = false,
        )
    }
}
