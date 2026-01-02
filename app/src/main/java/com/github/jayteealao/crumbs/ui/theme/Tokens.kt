package com.github.jayteealao.crumbs.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Duds Design System Tokens
 * Extracted from reference designs
 */

// ============================================
// COLOR TOKENS
// ============================================

object DudsColors {
    // Backgrounds & Surfaces
    val backgroundGradientStart = Color(0xFFE8D5FF) // Light purple
    val backgroundGradientMiddle = Color(0xFFD9FFD5) // Light mint green
    val backgroundGradientEnd = Color(0xFFF5FFCC) // Light yellow

    val surface = Color(0xFFFFFFFF) // White
    val surfaceDim = Color(0xFFF5F5F5) // Subtle gray

    // Accent & Interactive
    val primaryAccent = Color(0xFFD6FF00) // Neon yellow/lime
    val secondaryAccent = Color(0xFF000000) // Black
    val destructive = Color(0xFFFF3B30) // Red

    // Text
    val textPrimary = Color(0xFF000000) // Black
    val textSecondary = Color(0xFF666666) // Gray
    val textTertiary = Color(0xFF999999) // Light gray
    val textOnAccent = Color(0xFF000000) // Black on yellow
    val textOnDark = Color(0xFFFFFFFF) // White on black

    // Borders & Dividers
    val border = Color(0xFFE0E0E0) // Light gray
    val divider = Color.Transparent // Minimal dividers

    // Muted/Disabled
    val mutedFill = Color(0xFFF0F0F0)
    val disabledText = Color(0xFFCCCCCC)

    // Gradients
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            backgroundGradientStart,
            backgroundGradientMiddle,
            backgroundGradientEnd
        )
    )
}

// ============================================
// TYPOGRAPHY TOKENS
// ============================================

object DudsTypography {
    val h1Section = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 32.sp,
        letterSpacing = 0.5.sp
    )

    val h2CardTitle = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    )

    val bodyPrimary = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp
    )

    val bodySecondary = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp
    )

    val caption = TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp
    )

    val buttonLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp
    )

    val buttonSmall = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium
    )

    val chipLabel = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium
    )
}

// ============================================
// SPACING TOKENS
// ============================================

object DudsSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val base: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp
}

// ============================================
// RADIUS TOKENS
// ============================================

object DudsRadius {
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 20.dp
    val full: Dp = 9999.dp
}

// ============================================
// ELEVATION TOKENS
// ============================================

object DudsElevation {
    val none: Dp = 0.dp
    val subtle: Dp = 0.dp // Use border instead
    val card: Dp = 0.dp // Flat design
}

// ============================================
// ICON SIZES
// ============================================

object DudsIconSize {
    val small: Dp = 20.dp
    val standard: Dp = 24.dp
    val large: Dp = 32.dp
}
