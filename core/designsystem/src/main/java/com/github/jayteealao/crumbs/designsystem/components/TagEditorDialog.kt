package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList

// Brutalist TagEditorDialog — Material3 AlertDialog + OutlinedTextField stripped.
// Custom Dialog with sharp-edged Box, 1.5dp ink border. Mono kicker, Funnel
// Display title, autocomplete suggestions list, FlowRow of selected-tag chips,
// BasicTextField input, Row { Cancel | Save } actions.

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditorDialog(
    isVisible: Boolean,
    currentTags: ImmutableList<String>,
    availableTags: ImmutableList<String>,
    onDismiss: () -> Unit,
    onSave: (ImmutableList<String>) -> Unit
) {
    if (!isVisible) return

    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val stroke = LocalCrumbsStroke.current
    val typography = LocalCrumbsTypography.current

    var selectedTags by remember(currentTags) { mutableStateOf(currentTags.toPersistentList()) }
    var tagInput by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }

    val filteredSuggestions = remember(tagInput, availableTags, selectedTags) {
        if (tagInput.isBlank()) persistentListOf()
        else availableTags.filter { it.contains(tagInput, ignoreCase = true) && it !in selectedTags }
            .take(5)
            .toPersistentList()
    }
    LaunchedEffect(tagInput) {
        showSuggestions = tagInput.isNotBlank() && filteredSuggestions.isNotEmpty()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(colors.surface)
                .border(stroke.regular, colors.ink, RectangleShape)
                .testTag("tag-editor-dialog")
                .padding(spacing.md),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "↳ Add Tags".uppercase(),
                    style = typography.captionMono,
                    color = colors.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(spacing.xs))
                Text(
                    text = "Tag this bookmark",
                    style = typography.displaySmall,
                    color = colors.ink,
                )
                Spacer(modifier = Modifier.height(spacing.md))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(stroke.hairline, colors.ink, RectangleShape)
                        .padding(horizontal = spacing.sm, vertical = spacing.sm),
                ) {
                    BasicTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        textStyle = typography.bodyMono.copy(color = colors.ink),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tag-editor-input"),
                    )
                    if (tagInput.isBlank()) {
                        Text(
                            text = "Enter tag…",
                            style = typography.bodyMono,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                if (showSuggestions && filteredSuggestions.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .padding(vertical = spacing.xs),
                    ) {
                        items(filteredSuggestions) { suggestion ->
                            Text(
                                text = suggestion,
                                style = typography.bodyMono,
                                color = colors.ink,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (suggestion !in selectedTags) {
                                            selectedTags = (selectedTags + suggestion).toPersistentList()
                                        }
                                        tagInput = ""
                                        showSuggestions = false
                                    }
                                    .padding(spacing.sm),
                            )
                        }
                    }
                }
                if (tagInput.isNotBlank() && tagInput !in selectedTags) {
                    Spacer(modifier = Modifier.height(spacing.sm))
                    CrumbsButton(
                        onClick = {
                            selectedTags = (selectedTags + tagInput.trim()).toPersistentList()
                            tagInput = ""
                        },
                        text = "Add \"$tagInput\"",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (selectedTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(spacing.md))
                    Text(
                        text = "Selected".uppercase(),
                        style = typography.captionMono,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(spacing.xs))
                    FlowRow(modifier = Modifier.fillMaxWidth()) {
                        selectedTags.forEach { tag ->
                            Row(
                                modifier = Modifier.padding(end = spacing.xs, bottom = spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .border(stroke.hairline, colors.ink, RectangleShape)
                                        .background(colors.surface)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .testTag("tag-editor-chip-$tag"),
                                ) {
                                    Text(
                                        text = "#$tag",
                                        style = typography.tagMono,
                                        color = colors.ink,
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove tag",
                                    tint = colors.ink,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            selectedTags = (selectedTags - tag).toPersistentList()
                                        }
                                        .padding(start = 2.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(spacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    CrumbsButton(
                        onClick = onDismiss,
                        text = "Cancel",
                        style = ButtonStyle.Secondary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tag-editor-cancel"),
                    )
                    CrumbsButton(
                        onClick = {
                            onSave(selectedTags.toImmutableList())
                            onDismiss()
                        },
                        text = "Save",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tag-editor-save"),
                    )
                }
            }
        }
    }
}

@Preview(name = "Tag Editor Light", showBackground = true)
@Composable
private fun PreviewTagEditorLight() {
    CrumbsTheme(darkTheme = false) {
        TagEditorDialog(
            isVisible = true,
            currentTags = persistentListOf("programming", "design"),
            availableTags = persistentListOf("programming", "design", "android"),
            onDismiss = {},
            onSave = {},
        )
    }
}
