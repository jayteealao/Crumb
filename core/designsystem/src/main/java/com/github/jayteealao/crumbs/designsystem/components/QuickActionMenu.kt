package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

/**
 * Quick action data class
 *
 * Represents a single action in the quick action menu.
 *
 * @param label Text label for the action
 * @param icon Icon to display for the action
 * @param onClick Callback when the action is selected
 */
data class QuickAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/**
 * Context menu for long-press on bookmark cards
 *
 * Shows a dropdown menu with quick actions like Add Tags, Share, and Delete.
 * Triggered by long-pressing on a CrumbsBookmarkCard.
 *
 * @param visible Whether the menu is currently visible
 * @param onDismiss Callback when the menu should be dismissed
 * @param actions List of quick actions to display
 * @param modifier Modifier to be applied to the component
 */
@Composable
fun QuickActionMenu(
    visible: Boolean,
    onDismiss: () -> Unit,
    actions: List<QuickAction>,
    modifier: Modifier = Modifier
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val typography = LocalCrumbsTypography.current

    DropdownMenu(
        expanded = visible,
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        actions.forEach { action ->
            DropdownMenuItem(
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing.md)
                    ) {
                        // Action icon
                        Icon(
                            imageVector = action.icon,
                            contentDescription = null,
                            tint = colors.textPrimary
                        )

                        // Action label
                        Text(
                            text = action.label,
                            style = typography.bodyMedium,
                            color = colors.textPrimary
                        )
                    }
                },
                onClick = {
                    action.onClick()
                    onDismiss()
                }
            )
        }
    }
}

// Default quick actions for bookmarks
val defaultQuickActions = listOf(
    QuickAction("Add Tags", Icons.Default.LocalOffer) {},
    QuickAction("Share", Icons.Default.Share) {},
    QuickAction("Delete", Icons.Default.Delete) {}
)

// Previews
@Preview(name = "Quick Action Menu - Light", showBackground = true)
@Composable
private fun PreviewQuickActionMenuLight() {
    CrumbsTheme(darkTheme = false) {
        Surface {
            QuickActionMenu(
                visible = true,
                onDismiss = {},
                actions = defaultQuickActions
            )
        }
    }
}

@Preview(name = "Quick Action Menu - Dark", showBackground = true)
@Composable
private fun PreviewQuickActionMenuDark() {
    CrumbsTheme(darkTheme = true) {
        Surface {
            QuickActionMenu(
                visible = true,
                onDismiss = {},
                actions = defaultQuickActions
            )
        }
    }
}

@Preview(name = "Quick Action Menu - Custom Actions", showBackground = true)
@Composable
private fun PreviewQuickActionMenuCustom() {
    CrumbsTheme(darkTheme = false) {
        Surface {
            QuickActionMenu(
                visible = true,
                onDismiss = {},
                actions = listOf(
                    QuickAction("Edit", Icons.Default.LocalOffer) {},
                    QuickAction("Archive", Icons.Default.Share) {},
                    QuickAction("Mark as Read", Icons.Default.Delete) {}
                )
            )
        }
    }
}
