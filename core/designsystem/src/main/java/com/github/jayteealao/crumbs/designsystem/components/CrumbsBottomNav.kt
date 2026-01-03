package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors

import androidx.compose.ui.Modifier

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
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label
                    )
                },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.textPrimary,
                    selectedTextColor = colors.textPrimary,
                    indicatorColor = colors.accentAlpha,
                    unselectedIconColor = colors.textSecondary,
                    unselectedTextColor = colors.textSecondary
                )
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
