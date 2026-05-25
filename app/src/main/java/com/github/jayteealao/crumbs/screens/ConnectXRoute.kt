package com.github.jayteealao.crumbs.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import com.github.jayteealao.crumbs.Screens
import com.github.jayteealao.twitter.oauth.OAuthResult
import com.github.jayteealao.twitter.oauth.TwitterOAuthCoordinator
import com.github.jayteealao.twitter.screens.BookmarksViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session-scoped flag tracking whether the user dismissed the blocking
 * onboarding step with "Skip for now". Singleton so the NavHost route
 * guard reads the same flag the ConnectXRoute writes.
 */
@Singleton
class ConnectXSkipState @Inject constructor() {
    @Volatile var skipped: Boolean = false
}

@HiltViewModel
class ConnectXViewModel @Inject constructor(
    val skipState: ConnectXSkipState,
) : ViewModel()

/**
 * Navigation entry point for the ConnectX destination. Launches the Twitter OAuth Custom Tabs
 * flow via [TwitterOAuthCoordinator] and navigates home on success or shows a toast on failure.
 *
 * @param navController Used to navigate back to Home on OAuth success or after the user skips.
 * @param twitterOAuthCoordinator Drives the Custom Tabs OAuth launch and emits [OAuthResult] events.
 * @param bookmarksViewModel Refreshes sync status after a successful connection.
 * @param viewModel Holds the session-scoped [ConnectXSkipState] flag written on skip.
 */
@Composable
fun ConnectXRoute(
    navController: NavHostController,
    twitterOAuthCoordinator: TwitterOAuthCoordinator,
    bookmarksViewModel: BookmarksViewModel,
    viewModel: ConnectXViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var connecting by remember { mutableStateOf(false) }

    // Observe coordinator results; navigate forward on success.
    LaunchedEffect(twitterOAuthCoordinator) {
        twitterOAuthCoordinator.results.collect { result ->
            connecting = false
            when (result) {
                is OAuthResult.Success -> {
                    Toast.makeText(context, "Connected — bookmarks will appear soon", Toast.LENGTH_SHORT).show()
                    bookmarksViewModel.refreshSyncStatus()
                    navController.navigate(Screens.HOMESCREEN.screenRoute(false)) {
                        popUpTo(Screens.CONNECTX.name) { inclusive = true }
                    }
                }
                is OAuthResult.Failure -> {
                    Toast.makeText(
                        context,
                        "Couldn't connect (${result.reason}) — try again",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    ConnectXOnboardingScreen(
        connecting = connecting,
        onConnect = {
            val activity = context as? Activity ?: return@ConnectXOnboardingScreen
            connecting = true
            coroutineScope.launch {
                runCatching { twitterOAuthCoordinator.launchAuthorize(activity) }
                    .onFailure {
                        connecting = false
                        Toast.makeText(context, "Couldn't start sign-in — try again", Toast.LENGTH_LONG).show()
                    }
            }
        },
        onSkip = {
            viewModel.skipState.skipped = true
            navController.navigate(Screens.HOMESCREEN.screenRoute(false)) {
                popUpTo(Screens.CONNECTX.name) { inclusive = true }
            }
        },
    )
}
