package com.github.jayteealao.crumbs.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Duds Design System Color Palette
private val DudsLightColorPalette = lightColors(
    primary = DudsColors.primaryAccent,        // Yellow accent
    primaryVariant = DudsColors.secondaryAccent, // Black
    secondary = DudsColors.secondaryAccent,    // Black
    secondaryVariant = DudsColors.primaryAccent,
    background = DudsColors.surface,           // White (gradient applied separately)
    surface = DudsColors.surface,              // White
    error = DudsColors.destructive,            // Red
    onPrimary = DudsColors.textOnAccent,       // Black on yellow
    onSecondary = DudsColors.textOnDark,       // White on black
    onBackground = DudsColors.textPrimary,     // Black
    onSurface = DudsColors.textPrimary,        // Black
    onError = Color.White
)

// Dark mode (same as light for now - Duds is primarily light)
private val DudsDarkColorPalette = darkColors(
    primary = DudsColors.primaryAccent,
    primaryVariant = DudsColors.secondaryAccent,
    secondary = DudsColors.secondaryAccent
)

@Composable
fun CrumbTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    // For now, always use light theme (Duds design is light-mode focused)
    val colors = DudsLightColorPalette

    MaterialTheme(
        colors = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
