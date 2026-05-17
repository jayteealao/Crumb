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
