package com.github.jayteealao.crumbs.designsystem.theme

import androidx.compose.ui.graphics.Color

// Brutalist palette — high-contrast, paper + ink + a single accent.
// Accent is intentionally narrow: it's punctuation, not decoration.
data class CrumbsColors(
    val background: Color,
    val surface: Color,
    // also serves the JS 'rule' role — light collapses 'rule / ink' into one swatch
    // (handoff-tokens.jsx:21); dark separates the swatches but both = #FFFFFF (lines 41, 43).
    val ink: Color,
    val onSurfaceVariant: Color,
    val accent: Color,
    val onAccent: Color,
    val error: Color,
    val success: Color,
    val offsetShadow: Color,
)

val LightColors = CrumbsColors(
    background = Color(0xFFEFEEE9),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF0A0A0A),
    onSurfaceVariant = Color(0xFF535353),
    accent = Color(0xFFFF5A1F),
    onAccent = Color(0xFF0A0A0A),
    error = Color(0xFFA40000),
    success = Color(0xFF206040),
    offsetShadow = Color(0xFF0A0A0A),
)

val DarkColors = LightColors.copy(
    background = Color(0xFF0B0B0B),
    surface = Color(0xFF161616),
    ink = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF9A9A9A),
    offsetShadow = Color(0xFFFFFFFF),
)
