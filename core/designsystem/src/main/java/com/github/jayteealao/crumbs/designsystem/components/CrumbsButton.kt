package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

/**
 * Crumbs button with cut-corner styling
 *
 * Component variants for testing:
 * - Enabled/Disabled
 * - Small/Medium size
 * - Primary/Secondary style
 */

enum class ButtonSize {
    Small, Medium
}

enum class ButtonStyle {
    Primary, Secondary
}

@Composable
fun CrumbsButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: ButtonSize = ButtonSize.Medium,
    style: ButtonStyle = ButtonStyle.Primary
) {
    val colors = LocalCrumbsColors.current
    val typography = LocalCrumbsTypography.current

    val shape = when (size) {
        ButtonSize.Small -> CrumbsShapes.buttonSmall
        ButtonSize.Medium -> CrumbsShapes.button
    }

    val (containerColor, contentColor) = when (style) {
        ButtonStyle.Primary -> colors.primary to colors.textPrimary
        ButtonStyle.Secondary -> colors.surface to colors.textPrimary
    }

    val textStyle = when (size) {
        ButtonSize.Small -> typography.labelMedium
        ButtonSize.Medium -> typography.labelLarge
    }

    val contentPadding = when (size) {
        ButtonSize.Small -> PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ButtonSize.Medium -> PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    }

    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(
            minHeight = if (size == ButtonSize.Small) 36.dp else 48.dp
        ),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.5f),
            disabledContentColor = contentColor.copy(alpha = 0.5f)
        ),
        contentPadding = contentPadding
    ) {
        Text(
            text = text,
            style = textStyle
        )
    }
}

// Previews
@Preview(name = "Primary Medium Light", showBackground = true)
@Composable
private fun PreviewButtonPrimaryMediumLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsButton(
            onClick = {},
            text = "Click Me"
        )
    }
}

@Preview(name = "Primary Medium Dark", showBackground = true)
@Composable
private fun PreviewButtonPrimaryMediumDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsButton(
            onClick = {},
            text = "Click Me"
        )
    }
}

@Preview(name = "Disabled Light", showBackground = true)
@Composable
private fun PreviewButtonDisabledLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsButton(
            onClick = {},
            text = "Disabled",
            enabled = false
        )
    }
}

@Preview(name = "Small Secondary", showBackground = true)
@Composable
private fun PreviewButtonSmallSecondary() {
    CrumbsTheme(darkTheme = false) {
        CrumbsButton(
            onClick = {},
            text = "Small",
            size = ButtonSize.Small,
            style = ButtonStyle.Secondary
        )
    }
}
