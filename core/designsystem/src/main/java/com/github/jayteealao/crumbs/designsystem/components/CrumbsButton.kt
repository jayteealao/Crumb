package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

// Brutalist CrumbsButton — keeps the Material3 Button wrapper for signature
// stability (consumed by LoginScreen/OnboardingScreen) but overrides every
// chrome default so the result reads brutalist: sharp corners, 1.5dp ink
// border, zero elevation, accent fill for Primary, surface fill for Secondary.

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
    val stroke = LocalCrumbsStroke.current
    val shapes = LocalCrumbsShapes.current
    val typography = LocalCrumbsTypography.current

    val containerColor = when (style) {
        ButtonStyle.Primary -> colors.accent
        ButtonStyle.Secondary -> colors.surface
    }
    val contentColor = when (style) {
        ButtonStyle.Primary -> colors.onAccent
        ButtonStyle.Secondary -> colors.ink
    }

    val (minHeight, contentPadding) = when (size) {
        ButtonSize.Small -> 36.dp to PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ButtonSize.Medium -> 48.dp to PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    }

    Button(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .testTag("btn-${style.name.lowercase()}-${size.name.lowercase()}"),
        enabled = enabled,
        shape = shapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = colors.surface,
            disabledContentColor = colors.onSurfaceVariant,
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        border = BorderStroke(stroke.regular, colors.ink),
        contentPadding = contentPadding,
    ) {
        Text(
            text = text.uppercase(),
            style = typography.captionMono,
            color = contentColor,
        )
    }
}

// Previews
@Preview(name = "Primary Medium Light", showBackground = true)
@Composable
private fun PreviewButtonPrimaryMediumLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsButton(onClick = {}, text = "Click Me")
    }
}

@Preview(name = "Primary Medium Dark", showBackground = true)
@Composable
private fun PreviewButtonPrimaryMediumDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsButton(onClick = {}, text = "Click Me")
    }
}

@Preview(name = "Disabled Light", showBackground = true)
@Composable
private fun PreviewButtonDisabledLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsButton(onClick = {}, text = "Disabled", enabled = false)
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
            style = ButtonStyle.Secondary,
        )
    }
}
