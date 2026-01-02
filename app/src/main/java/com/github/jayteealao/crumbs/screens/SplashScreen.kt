package com.github.jayteealao.crumbs.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.jayteealao.crumbs.R
import com.github.jayteealao.crumbs.Screens
import com.github.jayteealao.crumbs.components.DudsLoadingIndicator
import com.github.jayteealao.crumbs.ui.theme.DudsColors
import com.github.jayteealao.twitter.screens.LoginViewModel
import kotlinx.coroutines.delay
import timber.log.Timber

@Composable
fun SplashScreen(
    isLoggedIn: Boolean,
    navController: NavController,
    loginViewModel: LoginViewModel
) {
    val alpha = remember { Animatable(0f) }
    val showLoading = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Fade in logo
        alpha.animateTo(1f, animationSpec = tween(300))
        delay(500)
        // Show loading indicator after 500ms
        showLoading.animateTo(1f, animationSpec = tween(200))
    }

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
        modifier = Modifier
            .fillMaxSize()
            .background(DudsColors.backgroundGradient)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_2),
                contentDescription = "Crumbs logo",
                modifier = Modifier
                    .size(120.dp)
                    .alpha(alpha.value)
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (showLoading.value > 0.5f) {
                DudsLoadingIndicator(
                    modifier = Modifier.alpha(showLoading.value)
                )
            }
        }
    }
}
