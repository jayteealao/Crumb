package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

/**
 * Sort options for bookmark lists
 */
enum class SortOption {
    Newest,
    Oldest,
    Popular,
    Random;

    /**
     * User-friendly display name for the sort option
     */
    fun displayName(): String = when (this) {
        Newest -> "Newest First"
        Oldest -> "Oldest First"
        Popular -> "Most Popular"
        Random -> "Random Order"
    }
}

/**
 * Dropdown menu for bookmark sorting options
 *
 * Shows a menu with sort options (Newest, Oldest, Popular, Random).
 * Displays check icon next to the currently selected option.
 *
 * @param expanded Whether the menu is currently visible
 * @param currentSort The currently selected sort option
 * @param onDismiss Callback when the menu should be dismissed
 * @param onSortSelected Callback when a sort option is selected
 * @param modifier Modifier to be applied to the component
 */
@Composable
fun CrumbsSortMenu(
    expanded: Boolean,
    currentSort: SortOption,
    onDismiss: () -> Unit,
    onSortSelected: (SortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCrumbsColors.current
    val typography = LocalCrumbsTypography.current

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        SortOption.values().forEach { sortOption ->
            val isSelected = sortOption == currentSort

            DropdownMenuItem(
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Check icon (visible when selected)
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = colors.accent
                            )
                        }

                        // Sort option text
                        Text(
                            text = sortOption.displayName(),
                            style = typography.bodyMedium,
                            color = if (isSelected) colors.accent else colors.textPrimary
                        )
                    }
                },
                onClick = {
                    onSortSelected(sortOption)
                    onDismiss()
                }
            )
        }
    }
}

// Previews
@Preview(name = "Sort Menu - Expanded with Newest Selected", showBackground = true)
@Composable
private fun PreviewSortMenuExpanded() {
    CrumbsTheme(darkTheme = false) {
        Surface {
            CrumbsSortMenu(
                expanded = true,
                currentSort = SortOption.Newest,
                onDismiss = {},
                onSortSelected = {}
            )
        }
    }
}

@Preview(name = "Sort Menu - Popular Selected", showBackground = true)
@Composable
private fun PreviewSortMenuPopular() {
    CrumbsTheme(darkTheme = false) {
        Surface {
            CrumbsSortMenu(
                expanded = true,
                currentSort = SortOption.Popular,
                onDismiss = {},
                onSortSelected = {}
            )
        }
    }
}

@Preview(name = "Sort Menu - Dark Theme", showBackground = true)
@Composable
private fun PreviewSortMenuDark() {
    CrumbsTheme(darkTheme = true) {
        Surface {
            CrumbsSortMenu(
                expanded = true,
                currentSort = SortOption.Random,
                onDismiss = {},
                onSortSelected = {}
            )
        }
    }
}
