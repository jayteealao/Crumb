package com.github.jayteealao.crumbs.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

/**
 * Crumbs theme provider
 *
 * Provides design tokens via CompositionLocal:
 * - Colors (light/dark)
 * - Typography
 * - Spacing
 *
 * Usage:
 * ```
 * CrumbsTheme {
 *     val colors = LocalCrumbsColors.current
 *     val spacing = LocalCrumbsSpacing.current
 *     // Use tokens in components
 * }
 * ```
 */

val LocalCrumbsColors = compositionLocalOf { LightColors }
val LocalCrumbsSpacing = compositionLocalOf { CrumbsSpacing }
val LocalCrumbsTypography = compositionLocalOf { CrumbsTypography }

@Composable
fun CrumbsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    CompositionLocalProvider(
        LocalCrumbsColors provides colors,
        LocalCrumbsSpacing provides CrumbsSpacing,
        LocalCrumbsTypography provides CrumbsTypography
    ) {
        content()
    }
}
