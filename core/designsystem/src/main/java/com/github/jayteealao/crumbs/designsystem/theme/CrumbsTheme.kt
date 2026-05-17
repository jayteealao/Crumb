package com.github.jayteealao.crumbs.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

// Theme provider for the brutalist token surface (colors, typography, spacing,
// stroke). The root container opts in to testTagsAsResourceId so Maestro flows
// (later slice) can address descendants by Modifier.testTag(...) name, and is
// tagged `app_root` for stable top-level addressing.

val LocalCrumbsColors = compositionLocalOf { LightColors }
val LocalCrumbsSpacing = compositionLocalOf { CrumbsSpacing }
val LocalCrumbsTypography = compositionLocalOf { CrumbsTypography }
val LocalCrumbsStroke = compositionLocalOf { CrumbsStroke }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CrumbsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    CompositionLocalProvider(
        LocalCrumbsColors provides colors,
        LocalCrumbsSpacing provides CrumbsSpacing,
        LocalCrumbsTypography provides CrumbsTypography,
        LocalCrumbsStroke provides CrumbsStroke,
    ) {
        Box(
            Modifier
                .semantics { testTagsAsResourceId = true }
                .testTag("app_root")
        ) {
            content()
        }
    }
}
