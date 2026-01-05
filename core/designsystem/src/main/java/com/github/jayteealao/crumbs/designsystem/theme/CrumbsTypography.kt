package com.github.jayteealao.crumbs.designsystem.theme

import android.os.Build
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
 */

/**
 * Detects if running in Robolectric test environment.
 * Robolectric sets Build.FINGERPRINT to "robolectric".
 */
private fun isRobolectric() = Build.FINGERPRINT == "robolectric"

/**
 * Font loading strategy that gracefully handles test environments.
 * - OptionalLocal in Robolectric: Falls back to system fonts if custom fonts fail to load
 * - Blocking in production: Ensures custom fonts are always loaded
 */
private val screenshotSafeFontStrategy =
    if (isRobolectric()) FontLoadingStrategy.OptionalLocal
    else FontLoadingStrategy.Blocking

/**
 * Funnel Display font family with test-safe loading strategy.
 * In Robolectric tests, fonts gracefully fall back to system defaults if TTF files can't load.
 */
private val FunnelDisplay = FontFamily(
    Font(R.font.funnel_display_regular, FontWeight.Normal, loadingStrategy = screenshotSafeFontStrategy),
    Font(R.font.funnel_display_medium, FontWeight.Medium, loadingStrategy = screenshotSafeFontStrategy),
    Font(R.font.funnel_display_semibold, FontWeight.SemiBold, loadingStrategy = screenshotSafeFontStrategy),
    Font(R.font.funnel_display_bold, FontWeight.Bold, loadingStrategy = screenshotSafeFontStrategy)
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
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal
    )

    val bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    )

    val labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium
    )

    val labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium
    )

    val caption = TextStyle(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal
    )
}
