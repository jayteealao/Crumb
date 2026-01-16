package com.github.jayteealao.crumbs.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.github.jayteealao.crumbs.designsystem.R

/**
 * Crumbs typography scale
 * Uses Funnel Display for display/heading text (bold, geometric)
 * Uses system default for body text (optimal readability)
 *
 * Font Loading Strategy: Async
 * - Loads fonts asynchronously in the background
 * - Falls back to system fonts if loading fails
 * - Non-blocking, doesn't crash the app
 */

/**
 * Funnel Display font family with async loading strategy.
 * Async strategy loads fonts in background and gracefully falls back to system fonts on failure.
 * This prevents crashes while still attempting to load custom fonts.
 */
private val FunnelDisplay = FontFamily(
    Font(
        resId = R.font.funnel_display_regular,
        weight = FontWeight.Normal,
        loadingStrategy = FontLoadingStrategy.Async
    ),
    Font(
        resId = R.font.funnel_display_medium,
        weight = FontWeight.Medium,
        loadingStrategy = FontLoadingStrategy.Async
    ),
    Font(
        resId = R.font.funnel_display_semibold,
        weight = FontWeight.SemiBold,
        loadingStrategy = FontLoadingStrategy.Async
    ),
    Font(
        resId = R.font.funnel_display_bold,
        weight = FontWeight.Bold,
        loadingStrategy = FontLoadingStrategy.Async
    )
)

object CrumbsTypography {
    val displayLarge = TextStyle(
        fontFamily = FunnelDisplay,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        fontWeight = FontWeight.Bold
    )

    val displayMedium = TextStyle(
        fontFamily = FunnelDisplay,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        fontWeight = FontWeight.Bold
    )

    val headingLarge = TextStyle(
        fontFamily = FunnelDisplay,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.SemiBold
    )

    val headingMedium = TextStyle(
        fontFamily = FunnelDisplay,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold
    )

    val titleLarge = TextStyle(
        fontFamily = FunnelDisplay,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Medium
    )

    val titleMedium = TextStyle(
        fontFamily = FunnelDisplay,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium
    )

    val bodyLarge = TextStyle(
        fontFamily = FunnelDisplay,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal
    )

    val bodyMedium = TextStyle(
        fontFamily = FunnelDisplay,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    )

    val labelLarge = TextStyle(
        fontFamily = FunnelDisplay,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium
    )

    val labelMedium = TextStyle(
        fontFamily = FunnelDisplay,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium
    )

    val caption = TextStyle(
        fontFamily = FunnelDisplay,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal
    )
}
