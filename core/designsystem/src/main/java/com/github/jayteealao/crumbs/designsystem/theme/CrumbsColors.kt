package com.github.jayteealao.crumbs.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Crumbs color palette - Modern Minimal
 * Neutral grays with electric cyan accent
 *
 * Light theme:
 * - #F8F9FA: Very light gray background
 * - #FFFFFF: Pure white surfaces
 * - #1F2937: Dark gray primary
 * - #00D9FF: Electric cyan accent
 *
 * Dark theme:
 * - #111827: Very dark gray background
 * - #1F2937: Dark gray surfaces
 * - #F9FAFB: Light gray primary
 * - #00D9FF: Electric cyan accent
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
    val navIndicator: Color,
    val error: Color = Color(0xFFEF4444)
)

val LightColors = CrumbsColors(
    background = Color(0xFFF8F9FA),    // Very light gray
    surface = Color(0xFFFFFFFF),       // Pure white
    primary = Color(0xFF1F2937),       // Dark gray
    accent = Color(0xFF00D9FF),        // Electric cyan
    textPrimary = Color(0xFF111827),   // Near black
    textSecondary = Color(0xFF6B7280), // Medium gray
    accentAlpha = Color(0xFF00D9FF).copy(alpha = 0.1f),
    surfaceVariant = Color(0xFFE5E7EB), // Light gray
    navIndicator = Color(0xFF00D9FF)   // Electric cyan
)

val DarkColors = CrumbsColors(
    background = Color(0xFF111827),    // Very dark gray
    surface = Color(0xFF1F2937),       // Dark gray
    primary = Color(0xFFF9FAFB),       // Light gray
    accent = Color(0xFF00D9FF),        // Electric cyan
    textPrimary = Color(0xFFF9FAFB),   // Near white
    textSecondary = Color(0xFF9CA3AF), // Light gray
    accentAlpha = Color(0xFF00D9FF).copy(alpha = 0.1f),
    surfaceVariant = Color(0xFF374151), // Medium dark gray
    navIndicator = Color(0xFF00D9FF)   // Electric cyan
)
