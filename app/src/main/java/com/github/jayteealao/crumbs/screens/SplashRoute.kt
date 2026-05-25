package com.github.jayteealao.crumbs.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.github.jayteealao.crumbs.Screens
import com.github.jayteealao.twitter.screens.LoginViewModel
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Navigation entry point for the splash destination. Waits 1 second, then routes to Home if a
 * Twitter access token is available (and attempts a token refresh), or to Login otherwise.
 *
 * @param navController Used to replace the splash back-stack entry with the appropriate destination.
 * @param loginViewModel Provides access-token availability and the token-refresh call.
 */
@Composable
fun SplashRoute(
    navController: NavController,
    loginViewModel: LoginViewModel = hiltViewModel(),
) {
    val isAccessTokenAvailable by loginViewModel.isAccessTokenAvailable.collectAsState()

    LaunchedEffect(isAccessTokenAvailable) {
        delay(1000)
        if (isAccessTokenAvailable) {
            val refreshed = loginViewModel.refreshToken()
            Timber.d("refreshed $refreshed")
            navController.currentBackStackEntry?.savedStateHandle?.set("refreshed", refreshed)
            navController.navigate(Screens.HOMESCREEN.screenRoute(true)) {
                popUpTo(Screens.SPLASHSCREEN.name) { inclusive = true }
            }
        } else {
            navController.navigate(Screens.LOGINSCREEN.name) {
                popUpTo(Screens.SPLASHSCREEN.name) { inclusive = true }
            }
        }
    }

    SplashScreen(uiState = SplashUiState(isLoggedIn = isAccessTokenAvailable))
}
