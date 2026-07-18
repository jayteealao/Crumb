package com.github.jayteealao.crumbs.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.github.jayteealao.crumbs.Screens
import com.github.jayteealao.crumbs.auth.SessionViewModel
import kotlinx.coroutines.delay

/**
 * Navigation entry point for the splash destination. Waits 1 second, then routes to Home if a
 * Firebase Auth session exists, or to Login otherwise.
 *
 * Routing is keyed on [SessionViewModel.isSignedIn] (the live Firebase Auth session), not the
 * legacy local Twitter token, so a signed-out user is always sent to Login even when a stale
 * X token remains in Prefs.
 *
 * @param navController Used to replace the splash back-stack entry with the appropriate destination.
 * @param sessionViewModel Provides Firebase Auth sign-in state for the routing decision.
 */
@Composable
fun SplashRoute(
    navController: NavController,
    sessionViewModel: SessionViewModel = hiltViewModel(),
) {
    val isSignedIn by sessionViewModel.isSignedIn.collectAsState()

    LaunchedEffect(isSignedIn) {
        delay(1000)
        if (isSignedIn) {
            navController.navigate(Screens.HOMESCREEN.screenRoute(true)) {
                popUpTo(Screens.SPLASHSCREEN.name) { inclusive = true }
            }
        } else {
            navController.navigate(Screens.LOGINSCREEN.name) {
                popUpTo(Screens.SPLASHSCREEN.name) { inclusive = true }
            }
        }
    }

    SplashScreen(uiState = SplashUiState(isLoggedIn = isSignedIn))
}
