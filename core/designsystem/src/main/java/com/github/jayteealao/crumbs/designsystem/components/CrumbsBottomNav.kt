package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

// Brutalist CrumbsBottomNav — Material3 NavigationBar stripped.
// Manual Row of 4 fixed-weight cells (TWITTER / REDDIT / ALL / MAP), each a
// clickable Box. Selected cell flips to ink background + accent text. No
// ripple. Hairline ink dividers between cells. 8dp safe-area below.

enum class BottomNavTab(val label: String) {
    TWITTER("Twitter"),
    REDDIT("Reddit"),
    ALL("All"),
    MAP("Map"),
}

@Composable
fun CrumbsBottomNav(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCrumbsColors.current
    val stroke = LocalCrumbsStroke.current
    val typography = LocalCrumbsTypography.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .testTag("bottom-nav"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stroke.regular)
                .background(colors.ink),
        )
        Row(modifier = Modifier.fillMaxWidth().height(52.dp)) {
            BottomNavTab.entries.forEachIndexed { index, tab ->
                val isSelected = tab == selectedTab
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (isSelected) colors.ink else Color.Transparent)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) { onTabSelected(tab) }
                        .testTag("nav-tab-${tab.name.lowercase()}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tab.label.uppercase(),
                        style = typography.captionMono,
                        color = if (isSelected) colors.accent else colors.ink,
                    )
                }
                if (index < BottomNavTab.entries.size - 1) {
                    Box(
                        modifier = Modifier
                            .width(stroke.hairline)
                            .fillMaxHeight()
                            .background(colors.ink),
                    )
                }
            }
        }
        Spacer(Modifier.fillMaxWidth().height(8.dp).background(colors.surface).padding(0.dp))
    }
}

// Previews
@Preview(name = "Twitter Selected Light", showBackground = true)
@Composable
private fun PreviewBottomNavTwitterLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsBottomNav(selectedTab = BottomNavTab.TWITTER, onTabSelected = {})
    }
}

@Preview(name = "Twitter Selected Dark", showBackground = true)
@Composable
private fun PreviewBottomNavTwitterDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsBottomNav(selectedTab = BottomNavTab.TWITTER, onTabSelected = {})
    }
}

@Preview(name = "All Selected Light", showBackground = true)
@Composable
private fun PreviewBottomNavAllLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsBottomNav(selectedTab = BottomNavTab.ALL, onTabSelected = {})
    }
}

@Preview(name = "Map Selected Light", showBackground = true)
@Composable
private fun PreviewBottomNavMapLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsBottomNav(selectedTab = BottomNavTab.MAP, onTabSelected = {})
    }
}
