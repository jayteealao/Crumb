package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Surface
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

/**
 * Crumbs tab bar with cut-corner styling for selected indicator
 *
 * Features:
 * - Three display styles: Standard (icon + text), Compact (icon only), Text (text only)
 * - Three sizes: Small (40dp), Medium (48dp), Large (56dp)
 * - Fixed or scrollable tabs
 * - Optional badge support for notification counts
 * - Cut-corner selection indicator matching button design language
 *
 * Component variants for testing:
 * - Style: Standard/Compact/Text
 * - Size: Small/Medium/Large
 * - Mode: Fixed/Scrollable
 * - Selected/Unselected states
 * - With/Without badges
 */

enum class TabBarStyle {
    Standard,   // Icon + text
    Compact,    // Icon only
    Text        // Text only
}

enum class TabSize {
    Small,      // 40.dp height
    Medium,     // 48.dp height
    Large       // 56.dp height
}

data class TabItem(
    val label: String,
    val icon: ImageVector? = null,
    val badgeCount: Int? = null
)

@Composable
fun CrumbsTabBar(
    tabs: List<TabItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: TabBarStyle = TabBarStyle.Standard,
    size: TabSize = TabSize.Medium,
    scrollable: Boolean = false
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val typography = LocalCrumbsTypography.current

    val height = when (size) {
        TabSize.Small -> 40.dp
        TabSize.Medium -> 48.dp
        TabSize.Large -> 56.dp
    }

    val textStyle = when (size) {
        TabSize.Small -> typography.labelMedium
        TabSize.Medium, TabSize.Large -> typography.labelLarge
    }

    val iconSize = when (size) {
        TabSize.Small -> 20.dp
        TabSize.Medium -> 24.dp
        TabSize.Large -> 28.dp
    }

    val indicator: @Composable (tabPositions: List<androidx.compose.material3.TabPosition>) -> Unit = { tabPositions ->
        if (selectedIndex < tabPositions.size) {
            Surface(
                modifier = Modifier
                    .tabIndicatorOffset(tabPositions[selectedIndex])
                    .height(3.dp),
                color = colors.accent,
                shape = CrumbsShapes.button  // Top-end cut corner for forward direction
            ) {}
        }
    }

    if (scrollable) {
        ScrollableTabRow(
            selectedTabIndex = selectedIndex,
            modifier = modifier.height(height),
            containerColor = colors.surface,
            contentColor = colors.textPrimary,
            indicator = indicator,
            divider = {}
        ) {
            tabs.forEachIndexed { index, tab ->
                TabContent(
                    tab = tab,
                    selected = index == selectedIndex,
                    onClick = { onTabSelected(index) },
                    style = style,
                    textStyle = textStyle,
                    iconSize = iconSize,
                    colors = colors,
                    spacing = spacing
                )
            }
        }
    } else {
        TabRow(
            selectedTabIndex = selectedIndex,
            modifier = modifier.height(height),
            containerColor = colors.surface,
            contentColor = colors.textPrimary,
            indicator = indicator,
            divider = {}
        ) {
            tabs.forEachIndexed { index, tab ->
                TabContent(
                    tab = tab,
                    selected = index == selectedIndex,
                    onClick = { onTabSelected(index) },
                    style = style,
                    textStyle = textStyle,
                    iconSize = iconSize,
                    colors = colors,
                    spacing = spacing
                )
            }
        }
    }
}

@Composable
private fun TabContent(
    tab: TabItem,
    selected: Boolean,
    onClick: () -> Unit,
    style: TabBarStyle,
    textStyle: androidx.compose.ui.text.TextStyle,
    iconSize: androidx.compose.ui.unit.Dp,
    colors: com.github.jayteealao.crumbs.designsystem.theme.CrumbsColors,
    spacing: com.github.jayteealao.crumbs.designsystem.theme.CrumbsSpacing
) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = if (style == TabBarStyle.Standard || style == TabBarStyle.Text) {
            {
                Text(
                    text = tab.label,
                    style = textStyle,
                    color = if (selected) colors.accent else colors.textSecondary
                )
            }
        } else null,
        icon = if (style == TabBarStyle.Standard || style == TabBarStyle.Compact) {
            {
                if (tab.badgeCount != null && tab.badgeCount > 0) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = colors.accent,
                                contentColor = colors.surface
                            ) {
                                Text(
                                    text = if (tab.badgeCount > 99) "99+" else tab.badgeCount.toString(),
                                    style = textStyle.copy(fontSize = textStyle.fontSize * 0.8f)
                                )
                            }
                        }
                    ) {
                        tab.icon?.let {
                            Icon(
                                imageVector = it,
                                contentDescription = tab.label,
                                modifier = Modifier.size(iconSize),
                                tint = if (selected) colors.accent else colors.textSecondary
                            )
                        }
                    }
                } else {
                    tab.icon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = tab.label,
                            modifier = Modifier.size(iconSize),
                            tint = if (selected) colors.accent else colors.textSecondary
                        )
                    }
                }
            }
        } else null
    )
}

// Previews
@Preview(name = "Standard Medium Light", showBackground = true)
@Composable
private fun PreviewTabBarStandardMediumLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsTabBar(
            tabs = listOf(
                TabItem("Home", Icons.Default.Home),
                TabItem("Search", Icons.Default.Search),
                TabItem("Settings", Icons.Default.Settings)
            ),
            selectedIndex = 0,
            onTabSelected = {}
        )
    }
}

@Preview(name = "Standard Medium Dark", showBackground = true)
@Composable
private fun PreviewTabBarStandardMediumDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsTabBar(
            tabs = listOf(
                TabItem("Home", Icons.Default.Home),
                TabItem("Search", Icons.Default.Search),
                TabItem("Settings", Icons.Default.Settings)
            ),
            selectedIndex = 1,
            onTabSelected = {}
        )
    }
}

@Preview(name = "Compact Small Light", showBackground = true)
@Composable
private fun PreviewTabBarCompactSmallLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsTabBar(
            tabs = listOf(
                TabItem("Home", Icons.Default.Home),
                TabItem("Search", Icons.Default.Search, badgeCount = 5),
                TabItem("Settings", Icons.Default.Settings)
            ),
            selectedIndex = 0,
            onTabSelected = {},
            style = TabBarStyle.Compact,
            size = TabSize.Small
        )
    }
}

@Preview(name = "Text Large Light", showBackground = true)
@Composable
private fun PreviewTabBarTextLargeLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsTabBar(
            tabs = listOf(
                TabItem("Overview"),
                TabItem("Details"),
                TabItem("Settings")
            ),
            selectedIndex = 2,
            onTabSelected = {},
            style = TabBarStyle.Text,
            size = TabSize.Large
        )
    }
}
