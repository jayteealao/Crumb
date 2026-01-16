package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.components.CrumbsButton
import com.github.jayteealao.crumbs.designsystem.components.CrumbsDialog
import com.github.jayteealao.crumbs.designsystem.components.CrumbsIconButton
import com.github.jayteealao.crumbs.designsystem.components.CrumbsTagChip
import com.github.jayteealao.crumbs.designsystem.components.CrumbsTextField
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

/**
 * Dialog for adding/editing tags on a bookmark
 *
 * @param isVisible Whether the dialog is visible
 * @param currentTags List of currently assigned tags
 * @param availableTags List of all available tags for autocomplete
 * @param onDismiss Called when dialog is dismissed without saving
 * @param onSave Called when tags are saved
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditorDialog(
    isVisible: Boolean,
    currentTags: List<String>,
    availableTags: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val typography = LocalCrumbsTypography.current

    var selectedTags by remember(currentTags) { mutableStateOf(currentTags.toMutableList()) }
    var tagInput by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }

    // Filter suggestions based on input
    val filteredSuggestions = remember(tagInput, availableTags, selectedTags) {
        if (tagInput.isBlank()) {
            emptyList()
        } else {
            availableTags
                .filter { it.contains(tagInput, ignoreCase = true) && !selectedTags.contains(it) }
                .take(5)
        }
    }

    LaunchedEffect(tagInput) {
        showSuggestions = tagInput.isNotBlank() && filteredSuggestions.isNotEmpty()
    }

    if (isVisible) {
        CrumbsDialog(
            onDismiss = onDismiss,
            title = "Add Tags",
            confirmButton = "Save" to {
                onSave(selectedTags)
                onDismiss()
            },
            dismissButton = "Cancel" to onDismiss,
            content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.md)
            ) {
                // Tag input field
                CrumbsTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    placeholder = "Enter tag...",
                    modifier = Modifier.fillMaxWidth()
                )

                // Autocomplete suggestions
                if (showSuggestions && filteredSuggestions.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = spacing.xs)
                    ) {
                        items(filteredSuggestions) { suggestion ->
                            Text(
                                text = suggestion,
                                style = typography.bodyMedium,
                                color = colors.textPrimary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!selectedTags.contains(suggestion)) {
                                            selectedTags.add(suggestion)
                                        }
                                        tagInput = ""
                                        showSuggestions = false
                                    }
                                    .padding(spacing.sm)
                            )
                        }
                    }
                }

                // Add tag button (when typing a new tag not in suggestions)
                if (tagInput.isNotBlank() && !selectedTags.contains(tagInput)) {
                    CrumbsButton(
                        onClick = {
                            selectedTags.add(tagInput.trim())
                            tagInput = ""
                        },
                        text = "Add \"$tagInput\"",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = spacing.xs)
                    )
                }

                // Selected tags
                if (selectedTags.isNotEmpty()) {
                    Text(
                        text = "Selected Tags",
                        style = typography.labelMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = spacing.md, bottom = spacing.xs)
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        selectedTags.forEach { tag ->
                            Row(
                                modifier = Modifier.padding(end = spacing.xs, bottom = spacing.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CrumbsTagChip(
                                    label = tag
                                )
                                CrumbsIconButton(
                                    onClick = { selectedTags.remove(tag) },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove tag",
                                            tint = colors.textPrimary
                                        )
                                    },
                                    contentDescription = "Remove tag"
                                )
                            }
                        }
                    }
                }
            }
            }
        )
    }
}
