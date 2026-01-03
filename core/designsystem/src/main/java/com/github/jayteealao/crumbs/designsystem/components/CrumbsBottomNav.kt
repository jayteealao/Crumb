package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

/**
 * Bottom navigation for Crumbs
 * Source-first organization: Twitter, Reddit, All, Map
 *
 * Component variants for testing:
 * - Each tab selected state
 * - Light/dark theme
 *
 * @param selectedTab The currently selected tab
 * @param onTabSelected Callback invoked when a tab is selected
 * @param modifier Modifier to be applied to the navigation bar
 */

enum class BottomNavTab(
    val label: String,
    val icon: ImageVector
) {
    TWITTER("Twitter", Icons.Default.Language),  // TODO: Replace with ic_twitter.xml
    REDDIT("Reddit", Icons.Default.Language),    // TODO: Replace with ic_reddit.xml
    ALL("All", Icons.Default.ViewList),
    MAP("Map", Icons.Default.Map)
}

@Composable
fun CrumbsBottomNav(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCrumbsColors.current

    NavigationBar(
        modifier = modifier,
        containerColor = colors.surface
    ) {
        BottomNavTab.entries.forEach { tab ->
            CrumbsNavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = tab.icon,
                label = tab.label
            )
        }
    }
}

/**
 * Custom navigation bar item with cut-corner selection indicator
 */
@Composable
private fun RowScope.CrumbsNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalCrumbsColors.current
    val typography = LocalCrumbsTypography.current
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .weight(1f)
            .clickable(
                onClick = onClick,
                enabled = true,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true, color = colors.accent)
            )
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Icon with background indicator when selected
            Box(
                contentAlignment = Alignment.Center
            ) {
                // Selection indicator with cut-corner shape
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(64.dp, 32.dp)
                            .clip(CrumbsShapes.navigationBar)
                            .background(colors.accentAlpha)
                    )
                }

                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) colors.textPrimary else colors.textSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Label
            Text(
                text = label,
                style = typography.labelMedium,
                color = if (selected) colors.textPrimary else colors.textSecondary
            )
        }
    }
}

// Previews
@Preview(name = "Twitter Selected Light", showBackground = true)
@Composable
private fun PreviewBottomNavTwitterLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsBottomNav(
            selectedTab = BottomNavTab.TWITTER,
            onTabSelected = {}
        )
    }
}

@Preview(name = "Twitter Selected Dark", showBackground = true)
@Composable
private fun PreviewBottomNavTwitterDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsBottomNav(
            selectedTab = BottomNavTab.TWITTER,
            onTabSelected = {}
        )
    }
}

@Preview(name = "Reddit Selected Light", showBackground = true)
@Composable
private fun PreviewBottomNavReddit() {
    CrumbsTheme(darkTheme = false) {
        CrumbsBottomNav(
            selectedTab = BottomNavTab.REDDIT,
            onTabSelected = {}
        )
    }
}

@Preview(name = "All Selected Light", showBackground = true)
@Composable
private fun PreviewBottomNavAllLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsBottomNav(
            selectedTab = BottomNavTab.ALL,
            onTabSelected = {}
        )
    }
}

@Preview(name = "Map Selected Light", showBackground = true)
@Composable
private fun PreviewBottomNavMapLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsBottomNav(
            selectedTab = BottomNavTab.MAP,
            onTabSelected = {}
        )
    }
}
