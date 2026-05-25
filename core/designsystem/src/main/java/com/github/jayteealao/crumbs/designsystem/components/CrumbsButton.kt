package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.modifiers.dashedBorder
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

// Brutalist CrumbsButton — rebuilt on Box.clickable (M3 Button stripped).
// Variants per handoff-components.jsx:558: Primary (accent bg) / Secondary
// (surface bg) / Ghost (transparent + dashed ink border) / Destructive
// (surface bg + error text + error border). Touch targets Small=44dp,
// Medium=56dp. Typography: displaySmall for Medium (CTA), labelLarge for
// Small (inline). No Material ripple — own-built indication=null.
//
// JS sig has no `size` param — kept as a deliberate Compose extension for
// inline-vs-CTA distinction (per RCA B1.10 LOW). Existing `text: String`
// overload is preserved as a thin wrapper over the content-slot overload.

enum class ButtonSize {
    Small, Medium
}

// JS-spec enum name. `ButtonStyle` is kept as a typealias so existing call
// sites that import it (`ButtonStyle.Primary` / `ButtonStyle.Secondary`)
// continue to compile.
enum class CrumbsButtonVariant {
    Primary, Secondary, Ghost, Destructive
}

typealias ButtonStyle = CrumbsButtonVariant

// --- Slot overload (canonical) — JS handoff-components.jsx:552-557.

@Composable
fun CrumbsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: ButtonSize = ButtonSize.Medium,
    style: CrumbsButtonVariant = CrumbsButtonVariant.Primary,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = LocalCrumbsColors.current
    val stroke = LocalCrumbsStroke.current
    val typography = LocalCrumbsTypography.current
    val interaction = remember { MutableInteractionSource() }

    val containerColor = when {
        !enabled -> colors.surface
        style == CrumbsButtonVariant.Primary -> colors.accent
        style == CrumbsButtonVariant.Secondary -> colors.surface
        style == CrumbsButtonVariant.Ghost -> Color.Transparent
        style == CrumbsButtonVariant.Destructive -> colors.surface
        else -> colors.surface
    }
    val contentColor = when {
        !enabled -> colors.onSurfaceVariant
        style == CrumbsButtonVariant.Primary -> colors.onAccent
        style == CrumbsButtonVariant.Destructive -> colors.error
        else -> colors.ink
    }
    val borderColor = when (style) {
        CrumbsButtonVariant.Destructive -> colors.error
        else -> colors.ink
    }
    val labelStyle: TextStyle = when (size) {
        ButtonSize.Small -> typography.labelLarge
        ButtonSize.Medium -> typography.displaySmall
    }
    val (minHeight, contentPadding) = when (size) {
        ButtonSize.Small -> 44.dp to PaddingValues(horizontal = 14.dp, vertical = 10.dp)
        ButtonSize.Medium -> 56.dp to PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    }

    val borderModifier: Modifier = if (style == CrumbsButtonVariant.Ghost) {
        Modifier.dashedBorder(width = stroke.regular, color = borderColor)
    } else {
        Modifier.border(stroke.regular, borderColor)
    }

    val buttonModifier = modifier
        .defaultMinSize(minHeight = minHeight)
        .background(containerColor)
        .then(borderModifier)
        .clickable(
            enabled = enabled,
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
        ) { onClick() }
        .padding(contentPadding)
        .testTag("btn-${style.name.lowercase()}-${size.name.lowercase()}")

    Row(
        modifier = buttonModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(labelStyle.copy(color = contentColor)) {
                content()
            }
        }
    }
}

// --- Text-string convenience overload (kept for the many call sites that
// don't need the full content slot).

@Composable
fun CrumbsButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: ButtonSize = ButtonSize.Medium,
    style: CrumbsButtonVariant = CrumbsButtonVariant.Primary,
) {
    CrumbsButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        size = size,
        style = style,
    ) {
        Text(text = text.uppercase())
    }
}

@Composable
private fun ProvideTextStyle(value: TextStyle, content: @Composable () -> Unit) {
    val merged = androidx.compose.material3.LocalTextStyle.current.merge(value)
    CompositionLocalProvider(androidx.compose.material3.LocalTextStyle provides merged, content = content)
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
            style = CrumbsButtonVariant.Secondary,
        )
    }
}

@Preview(name = "Ghost", showBackground = true)
@Composable
private fun PreviewButtonGhost() {
    CrumbsTheme(darkTheme = false) {
        CrumbsButton(onClick = {}, text = "Ghost", style = CrumbsButtonVariant.Ghost)
    }
}

@Preview(name = "Destructive", showBackground = true)
@Composable
private fun PreviewButtonDestructive() {
    CrumbsTheme(darkTheme = false) {
        CrumbsButton(onClick = {}, text = "Delete", style = CrumbsButtonVariant.Destructive)
    }
}
