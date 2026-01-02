package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.DudsColors
import com.github.jayteealao.crumbs.designsystem.theme.DudsTypography

/**
 * DudsBottomNav - Bottom navigation bar with 3 items and center FAB
 *
 * @param selectedItem The currently selected item (0 = left, 2 = right)
 * @param onItemSelected Callback when an item is selected
 * @param onFabClick Callback when the center FAB is clicked
 */
@Composable
fun DudsBottomNav(
    selectedItem: Int = 0,
    onItemSelected: (Int) -> Unit = {},
    onFabClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
        color = DudsColors.surface,
        elevation = 8.dp
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left item - Feed
                BottomNavItem(
                    icon = Icons.Default.Home,
                    label = "feed",
                    selected = selectedItem == 0,
                    onClick = { onItemSelected(0) }
                )

                // Spacer for center FAB
                Box(modifier = Modifier.size(56.dp))

                // Right item - Profile
                BottomNavItem(
                    icon = Icons.Default.Person,
                    label = "profile",
                    selected = selectedItem == 2,
                    onClick = { onItemSelected(2) }
                )
            }

            // Center FAB
            FloatingActionButton(
                onClick = onFabClick,
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = 12.dp),
                backgroundColor = DudsColors.primaryAccent,
                contentColor = DudsColors.textOnAccent
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = "Toggle view",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) DudsColors.textPrimary else DudsColors.textSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = label,
            style = DudsTypography.caption,
            color = if (selected) DudsColors.textPrimary else DudsColors.textSecondary
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DudsBottomNavPreview() {
    Column {
        DudsBottomNav(selectedItem = 0)
    }
}
