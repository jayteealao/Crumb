package com.github.jayteealao.crumbs.designsystem

import androidx.compose.runtime.Composable
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme

/**
 * Test-specific theme wrapper that uses system fonts instead of custom fonts.
 * This allows screenshot tests to run in NATIVE graphics mode.
 *
 * Simply uses CrumbsTheme directly, relying on font fallback in NATIVE mode.
 * The system will use default fonts when custom fonts fail to load.
 *
 * Usage in tests:
 * ```kotlin
 * TestCrumbsTheme(darkTheme = false) {
 *     YourComponent()
 * }
 * ```
 */
@Composable
fun TestCrumbsTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    // In NATIVE mode with font fallback enabled, this will use system fonts
    // when the custom Funnel Display fonts can't be loaded
    CrumbsTheme(darkTheme = darkTheme) {
        content()
    }
}
