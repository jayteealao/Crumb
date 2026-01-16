package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

/**
 * Crumbs text field with cut-corner styling
 *
 * Features:
 * - Two styles: Filled (solid background) and Outlined (border only)
 * - Cut-corner shape (bottom-start) for input area
 * - Theme-aware colors with proper focus/error states
 * - Support for icons, labels, placeholders, and supporting text
 * - Single-line and multi-line support
 *
 * Component variants for testing:
 * - Style: Filled/Outlined
 * - State: Default/Focused/Error/Disabled
 * - With/without label, leading/trailing icons
 */

enum class TextFieldStyle {
    Filled,
    Outlined
}

@Composable
fun CrumbsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextFieldStyle = TextFieldStyle.Filled,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val colors = LocalCrumbsColors.current
    val typography = LocalCrumbsTypography.current

    when (style) {
        TextFieldStyle.Filled -> TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            label = label?.let { { Text(it, style = typography.labelMedium) } },
            placeholder = placeholder?.let { { Text(it, style = typography.bodyLarge) } },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            supportingText = supportingText?.let { { Text(it, style = typography.labelMedium) } },
            isError = isError,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = typography.bodyLarge,
            shape = CrumbsShapes.textField,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceVariant,
                unfocusedContainerColor = colors.surfaceVariant,
                disabledContainerColor = colors.surfaceVariant.copy(alpha = 0.38f),
                errorContainerColor = colors.surfaceVariant,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                disabledTextColor = colors.textPrimary.copy(alpha = 0.38f),
                errorTextColor = colors.textPrimary,
                focusedIndicatorColor = colors.accent,
                unfocusedIndicatorColor = colors.textSecondary.copy(alpha = 0.3f),
                disabledIndicatorColor = colors.textSecondary.copy(alpha = 0.12f),
                errorIndicatorColor = colors.error,
                focusedLabelColor = colors.accent,
                unfocusedLabelColor = colors.textSecondary,
                disabledLabelColor = colors.textSecondary.copy(alpha = 0.38f),
                errorLabelColor = colors.error,
                cursorColor = colors.accent,
                errorCursorColor = colors.error
            ),
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions
        )

        TextFieldStyle.Outlined -> OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            label = label?.let { { Text(it, style = typography.labelMedium) } },
            placeholder = placeholder?.let { { Text(it, style = typography.bodyLarge) } },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            supportingText = supportingText?.let { { Text(it, style = typography.labelMedium) } },
            isError = isError,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = typography.bodyLarge,
            shape = CrumbsShapes.textField,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                disabledContainerColor = colors.surface.copy(alpha = 0.38f),
                errorContainerColor = colors.surface,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                disabledTextColor = colors.textPrimary.copy(alpha = 0.38f),
                errorTextColor = colors.textPrimary,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.textSecondary.copy(alpha = 0.3f),
                disabledBorderColor = colors.textSecondary.copy(alpha = 0.12f),
                errorBorderColor = colors.error,
                focusedLabelColor = colors.accent,
                unfocusedLabelColor = colors.textSecondary,
                disabledLabelColor = colors.textSecondary.copy(alpha = 0.38f),
                errorLabelColor = colors.error,
                cursorColor = colors.accent,
                errorCursorColor = colors.error
            ),
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions
        )
    }
}

// Previews
@Preview(name = "Filled Default Light", showBackground = true)
@Composable
private fun PreviewTextFieldFilledDefaultLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsTextField(
            value = "",
            onValueChange = {},
            label = "Label",
            placeholder = "Enter text",
            style = TextFieldStyle.Filled
        )
    }
}

@Preview(name = "Filled Default Dark", showBackground = true)
@Composable
private fun PreviewTextFieldFilledDefaultDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsTextField(
            value = "Sample text",
            onValueChange = {},
            label = "Label",
            style = TextFieldStyle.Filled
        )
    }
}

@Preview(name = "Outlined Default Light", showBackground = true)
@Composable
private fun PreviewTextFieldOutlinedDefaultLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsTextField(
            value = "",
            onValueChange = {},
            label = "Label",
            placeholder = "Enter text",
            style = TextFieldStyle.Outlined
        )
    }
}

@Preview(name = "Filled With Icons Light", showBackground = true)
@Composable
private fun PreviewTextFieldFilledWithIconsLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsTextField(
            value = "Search query",
            onValueChange = {},
            placeholder = "Search...",
            leadingIcon = {
                Icon(Icons.Default.Search, "Search")
            },
            trailingIcon = {
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Clear, "Clear")
                }
            },
            style = TextFieldStyle.Filled
        )
    }
}

@Preview(name = "Filled Error Light", showBackground = true)
@Composable
private fun PreviewTextFieldFilledErrorLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsTextField(
            value = "Invalid input",
            onValueChange = {},
            label = "Email",
            supportingText = "Please enter a valid email",
            isError = true,
            style = TextFieldStyle.Filled
        )
    }
}

@Preview(name = "Filled Disabled Light", showBackground = true)
@Composable
private fun PreviewTextFieldFilledDisabledLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsTextField(
            value = "Disabled field",
            onValueChange = {},
            label = "Label",
            enabled = false,
            style = TextFieldStyle.Filled
        )
    }
}
