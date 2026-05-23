package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsColors

// Sealed class for the CrumbsTopBar trailing action button — matches the JS
// spec at handoff-components.jsx:455-462. Each data object carries the icon
// and accessibility label; `bg(colors)` returns the cell background per
// variant (accent for Search, surface for the others).
sealed class TopBarAction(
    val icon: ImageVector,
    val label: String,
) {
    data object Search : TopBarAction(Icons.Default.Search, "Search")
    data object SwitchToFeed : TopBarAction(Icons.AutoMirrored.Filled.ViewList, "Switch to feed")
    data object Close : TopBarAction(Icons.Default.Close, "Close")

    @Composable
    fun bg(colors: CrumbsColors): Color = when (this) {
        Search -> colors.accent
        else -> colors.surface
    }
}
