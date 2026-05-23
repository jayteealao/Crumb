package com.github.jayteealao.crumbs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.github.jayteealao.crumbs.screens.ConnectXRoute
import com.github.jayteealao.crumbs.screens.HomeRoute
import com.github.jayteealao.crumbs.screens.OnboardingRoute
import com.github.jayteealao.crumbs.screens.SearchRoute
import com.github.jayteealao.crumbs.screens.SettingsRoute
import com.github.jayteealao.crumbs.screens.SplashRoute
import com.github.jayteealao.crumbs.screens.ThreadDetailRoute
import com.github.jayteealao.crumbs.screens.login.LoginRoute
import com.github.jayteealao.twitter.oauth.TwitterOAuthCoordinator
import com.github.jayteealao.twitter.screens.BookmarksViewModel
import com.github.jayteealao.twitter.screens.LoginViewModel

@Composable
fun CrumbsNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screens.SPLASHSCREEN.name,
    loginViewModel: LoginViewModel = hiltViewModel(),
    bookmarksViewModel: BookmarksViewModel = hiltViewModel(),
    twitterOAuthCoordinator: TwitterOAuthCoordinator,
) {
    // Refresh sync_status on every ON_START so the reconnect banner state
    // reflects reality after the user returns from Custom Tabs or the Play
    // Store. The repository throttles to one read per 5s internally.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                bookmarksViewModel.refreshSyncStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

        composable(Screens.CONNECTX.name) {
            ConnectXRoute(
                navController = navController,
                twitterOAuthCoordinator = twitterOAuthCoordinator,
                bookmarksViewModel = bookmarksViewModel,
            )
        }

        composable(Screens.SETTINGS.name) {
            SettingsRoute(
                navController = navController,
                bookmarksViewModel = bookmarksViewModel,
            )
        }

        composable(Screens.SPLASHSCREEN.name) {
            SplashRoute(
                navController = navController,
                loginViewModel = loginViewModel,
            )
        }

        composable(Screens.SEARCHSCREEN.name) {
            SearchRoute(navController = navController)
        }

        composable(
            "${Screens.THREADDETAIL.name}?bookmarkId={bookmarkId}",
            arguments = listOf(
                navArgument("bookmarkId") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
        ) {
            ThreadDetailRoute(navController = navController)
        }
    }
}

enum class Screens {
    SPLASHSCREEN,
    ONBOARDING,
    LOGINSCREEN,
    CONNECTX,
    SETTINGS,
    SEARCHSCREEN,
    THREADDETAIL,
    HOMESCREEN {
        override fun screenRoute(refreshed: Boolean) = "${this.name}/$refreshed"
    };
    open fun screenRoute(refreshed: Boolean) = this.name
}
