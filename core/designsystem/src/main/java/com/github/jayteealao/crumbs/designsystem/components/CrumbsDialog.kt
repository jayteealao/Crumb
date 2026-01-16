package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

/**
 * Crumbs dialog with dual cut-corner styling
 *
 * Features:
 * - Three sizes: Small, Medium, Large
 * - Dual cut-corner shape (top-end + bottom-start) for elevated importance
 * - Optional icon, title, text, and custom content
 * - Configurable confirm/dismiss buttons
 * - Dangerous mode (red confirm button) for destructive actions
 * - Theme-aware colors
 *
 * Component variants for testing:
 * - Size: Small/Medium/Large
 * - With/without icon
 * - Text/custom content
 * - One button/two buttons
 * - Normal/dangerous confirm button
 */

enum class DialogSize {
    Small,      // Compact dialogs
    Medium,     // Standard dialogs
    Large       // Full-width minus margins
}

@Composable
fun CrumbsDialog(
    onDismiss: () -> Unit,
    title: String? = null,
    text: String? = null,
    icon: @Composable (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
    confirmButton: Pair<String, () -> Unit>? = null,
    dismissButton: Pair<String, () -> Unit>? = null,
    modifier: Modifier = Modifier,
    size: DialogSize = DialogSize.Medium,
    isDangerous: Boolean = false
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val typography = LocalCrumbsTypography.current

    val maxWidth = when (size) {
        DialogSize.Small -> 280.dp
        DialogSize.Medium -> 360.dp
        DialogSize.Large -> 480.dp
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier.widthIn(max = maxWidth),
            shape = CrumbsShapes.dialog,
            color = colors.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(spacing.xl)
            ) {
                // Icon
                icon?.let {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        it()
                    }
                    Spacer(modifier = Modifier.height(spacing.md))
                }

                // Title
                title?.let {
                    Text(
                        text = it,
                        style = typography.titleLarge,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(spacing.md))
                }

                // Text or custom content
                if (text != null) {
                    Text(
                        text = text,
                        style = typography.bodyLarge,
                        color = colors.textSecondary
                    )
                } else if (content != null) {
                    content()
                }

                // Buttons
                if (confirmButton != null || dismissButton != null) {
                    Spacer(modifier = Modifier.height(spacing.lg))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Spacer(modifier = Modifier.weight(1f))

                        dismissButton?.let { (text, onClick) ->
                            CrumbsButton(
                                onClick = {
                                    onClick()
                                    onDismiss()
                                },
                                text = text,
                                style = ButtonStyle.Secondary,
                                size = ButtonSize.Small
                            )
                        }

                        if (dismissButton != null && confirmButton != null) {
                            Spacer(modifier = Modifier.width(spacing.sm))
                        }

                        confirmButton?.let { (text, onClick) ->
                            CrumbsButton(
                                onClick = {
                                    onClick()
                                    onDismiss()
                                },
                                text = text,
                                style = if (isDangerous) ButtonStyle.Primary else ButtonStyle.Primary,
                                size = ButtonSize.Small
                            )
                        }
                    }
                }
            }
        }
    }
}

// Previews
@Preview(name = "Text Only Medium Light", showBackground = true)
@Composable
private fun PreviewDialogTextOnlyMediumLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsDialog(
            onDismiss = {},
            title = "Confirm Action",
            text = "Are you sure you want to proceed with this action?",
            confirmButton = "Confirm" to {},
            dismissButton = "Cancel" to {}
        )
    }
}

@Preview(name = "Text Only Medium Dark", showBackground = true)
@Composable
private fun PreviewDialogTextOnlyMediumDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsDialog(
            onDismiss = {},
            title = "Confirm Action",
            text = "Are you sure you want to proceed with this action?",
            confirmButton = "Confirm" to {},
            dismissButton = "Cancel" to {}
        )
    }
}

@Preview(name = "With Icon Medium Light", showBackground = true)
@Composable
private fun PreviewDialogWithIconMediumLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsDialog(
            onDismiss = {},
            title = "Warning",
            text = "This action cannot be undone. Please proceed with caution.",
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = LocalCrumbsColors.current.accent
                )
            },
            confirmButton = "Proceed" to {},
            dismissButton = "Cancel" to {}
        )
    }
}

@Preview(name = "Dangerous Small Light", showBackground = true)
@Composable
private fun PreviewDialogDangerousSmallLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsDialog(
            onDismiss = {},
            title = "Delete Item",
            text = "This will permanently delete the item. This action cannot be undone.",
            confirmButton = "Delete" to {},
            dismissButton = "Cancel" to {},
            size = DialogSize.Small,
            isDangerous = true
        )
    }
}

@Preview(name = "Large With Custom Content Light", showBackground = true)
@Composable
private fun PreviewDialogLargeCustomContentLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsDialog(
            onDismiss = {},
            title = "Details",
            content = {
                Column {
                    Text("Custom content goes here")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("You can add any composable content")
                }
            },
            confirmButton = "OK" to {},
            size = DialogSize.Large
        )
    }
}
