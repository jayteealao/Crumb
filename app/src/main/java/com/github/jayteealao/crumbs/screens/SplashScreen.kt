package com.github.jayteealao.crumbs.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.jayteealao.crumbs.R
import com.github.jayteealao.crumbs.Screens
import com.github.jayteealao.twitter.screens.LoginViewModel
import kotlinx.coroutines.delay
import timber.log.Timber

@Composable
fun SplashScreen(
    isLoggedIn: Boolean,
    navController: NavController,
    loginViewModel: LoginViewModel
) {
    LaunchedEffect(isLoggedIn) {
        delay(1000)
        if (isLoggedIn) {
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
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_2),
            contentDescription = "",
            modifier = Modifier.size(300.dp)
        )
    }
}
