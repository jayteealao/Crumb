package com.github.jayteealao.crumbs.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Crumbs color palette
 * - #c2e7da: Soft mint/seafoam
 * - #28666e: Deep teal
 * - #f1ffe7: Pale cream
 * - #1a1b41: Dark navy-purple
 * - #baff29: Neon lime
 */
data class CrumbsColors(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accentAlpha: Color,
    val surfaceVariant: Color,
    val error: Color = Color(0xFFFF5555)
)

val LightColors = CrumbsColors(
    background = Color(0xFFF1FFE7),    // Pale cream
    surface = Color(0xFFC2E7DA),       // Mint
    primary = Color(0xFF28666E),       // Deep teal
    accent = Color(0xFFBAFF29),        // Neon lime
    textPrimary = Color(0xFF1A1B41),   // Dark navy
    textSecondary = Color(0xFF28666E).copy(alpha = 0.7f),
    accentAlpha = Color(0xFFBAFF29).copy(alpha = 0.2f),
    surfaceVariant = Color(0xFFC2E7DA).copy(alpha = 0.5f)
)

val DarkColors = CrumbsColors(
    background = Color(0xFF1A1B41),    // Dark navy
    surface = Color(0xFF28666E),       // Teal
    primary = Color(0xFFC2E7DA),       // Mint
    accent = Color(0xFFBAFF29),        // Neon lime
    textPrimary = Color(0xFFF1FFE7),   // Cream
    textSecondary = Color(0xFFC2E7DA).copy(alpha = 0.7f),
    accentAlpha = Color(0xFFBAFF29).copy(alpha = 0.2f),
    surfaceVariant = Color(0xFF28666E).copy(alpha = 0.5f)
)
