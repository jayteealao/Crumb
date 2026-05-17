package com.github.jayteealao.crumbs.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

@androidx.compose.runtime.Immutable
data class SplashUiState(
    val isLoggedIn: Boolean,
)

@Composable
fun SplashScreen(
    uiState: SplashUiState,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCrumbsColors.current
    val typography = LocalCrumbsTypography.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("splash-screen"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "crumbs•",
            style = typography.displayHeadline,
            color = colors.ink,
            modifier = Modifier.testTag("splash-wordmark"),
        )
    }
}

@Preview(name = "Splash Light", showBackground = true)
@Composable
private fun PreviewSplashLight() {
    CrumbsTheme(darkTheme = false) {
        SplashScreen(uiState = SplashUiState(isLoggedIn = false))
    }
}

@Preview(name = "Splash Dark", showBackground = true)
@Composable
private fun PreviewSplashDark() {
    CrumbsTheme(darkTheme = true) {
        SplashScreen(uiState = SplashUiState(isLoggedIn = true))
    }
}
