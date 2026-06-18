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

@Deprecated(
    message = "Use SplashScreen(uiState, modifier) instead",
    replaceWith = ReplaceWith("SplashScreen(uiState = SplashUiState(isLoggedIn = isLoggedIn), modifier = modifier)"),
)
@Composable
@Suppress("UNUSED_PARAMETER")
fun SplashScreen(
    isLoggedIn: Boolean,
    navController: androidx.navigation.NavController,
    loginViewModel: com.github.jayteealao.twitter.screens.LoginViewModel,
) {
    SplashScreen(uiState = SplashUiState(isLoggedIn = isLoggedIn))
}

/**
 * Splash / launch screen shown while the app determines navigation destination. Displays only
 * the wordmark on the theme background; the [SplashRoute] drives the timed redirect to Login
 * or Home based on [SplashUiState.isLoggedIn].
 *
 * @param uiState Holds [SplashUiState.isLoggedIn] so the route can read auth state before navigating.
 */
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
