package com.github.jayteealao.crumbs

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.github.jayteealao.crumbs.screens.HomeScreen
import com.github.jayteealao.crumbs.screens.OnboardingScreen
import com.github.jayteealao.crumbs.screens.SplashScreen
import com.github.jayteealao.crumbs.screens.login.LoginScreen
import com.github.jayteealao.twitter.screens.LoginViewModel
import com.github.jayteealao.twitter.screens.BookmarksViewModel

@Composable
fun CrumbsNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screens.SPLASHSCREEN.name,
    loginViewModel: LoginViewModel = hiltViewModel(),
    bookmarksViewModel: BookmarksViewModel = hiltViewModel()
) {
    val isAccessTokenAvailable by loginViewModel.isAccessTokenAvailable.collectAsState()

    NavHost(
        navController = navController,
        modifier = modifier,
        startDestination = startDestination
    ) {
        composable(Screens.ONBOARDING.name) {
            OnboardingScreen(navController = navController)
        }

        composable(
            Screens.LOGINSCREEN.name,
            deepLinks = listOf(navDeepLink { uriPattern = "crumbs://graphitenerd.xyz?code={code}" })
        ) {
            LoginScreen(
                viewModel = loginViewModel,
                navController = navController,
                authorizationCode = navController
                    .currentBackStackEntry?.arguments?.getString("code")
            )
        }

        composable(
            "${Screens.HOMESCREEN.name}/{refreshed}",
            arguments = listOf(navArgument(name = "refreshed") { type = NavType.BoolType })
        ) {
            HomeScreen(
                navController = navController,
                loginViewModel = loginViewModel,
                bookmarksViewModel = bookmarksViewModel,
                twitterAuthCode = navController
                    .currentBackStackEntry?.arguments?.getString("code") ?: ""
            )
        }

        composable(
            Screens.SPLASHSCREEN.name
        ) {
            SplashScreen(
                isLoggedIn = isAccessTokenAvailable,
                navController = navController,
                loginViewModel = loginViewModel
            )
        }
    }
}

enum class Screens {
    SPLASHSCREEN,
    ONBOARDING,
    LOGINSCREEN,
    HOMESCREEN {
        override fun screenRoute(refreshed: Boolean) = "${this.name}/$refreshed"
    };
    open fun screenRoute(refreshed: Boolean) = this.name
}
