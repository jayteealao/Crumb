package com.github.jayteealao.crumbs

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.github.jayteealao.crumbs.screens.HomeRoute
import com.github.jayteealao.crumbs.screens.OnboardingRoute
import com.github.jayteealao.crumbs.screens.SplashRoute
import com.github.jayteealao.crumbs.screens.login.LoginRoute
import com.github.jayteealao.twitter.screens.BookmarksViewModel
import com.github.jayteealao.twitter.screens.LoginViewModel

@Composable
fun CrumbsNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screens.SPLASHSCREEN.name,
    loginViewModel: LoginViewModel = hiltViewModel(),
    bookmarksViewModel: BookmarksViewModel = hiltViewModel(),
) {
    NavHost(
        navController = navController,
        modifier = modifier,
        startDestination = startDestination,
    ) {
        composable(Screens.ONBOARDING.name) {
            OnboardingRoute(navController = navController)
        }

        composable(
            Screens.LOGINSCREEN.name,
            deepLinks = listOf(navDeepLink { uriPattern = "crumbs://graphitenerd.xyz?code={code}" }),
        ) {
            LoginRoute(
                navController = navController,
                authorizationCode = navController
                    .currentBackStackEntry?.arguments?.getString("code"),
                loginViewModel = loginViewModel,
            )
        }

        composable(
            "${Screens.HOMESCREEN.name}/{refreshed}",
            arguments = listOf(navArgument(name = "refreshed") { type = NavType.BoolType }),
        ) {
            HomeRoute(
                navController = navController,
                twitterAuthCode = navController
                    .currentBackStackEntry?.arguments?.getString("code") ?: "",
                loginViewModel = loginViewModel,
                bookmarksViewModel = bookmarksViewModel,
            )
        }

        composable(Screens.SPLASHSCREEN.name) {
            SplashRoute(
                navController = navController,
                loginViewModel = loginViewModel,
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
