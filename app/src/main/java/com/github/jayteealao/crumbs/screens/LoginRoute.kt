package com.github.jayteealao.crumbs.screens.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.github.jayteealao.crumbs.BuildConfig
import com.github.jayteealao.crumbs.Screens
import com.github.jayteealao.reddit.screens.RedditViewModel
import com.github.jayteealao.twitter.screens.LoginViewModel
import kotlinx.coroutines.delay
import timber.log.Timber

@Composable
fun LoginRoute(
    navController: NavController,
    authorizationCode: String? = null,
    loginViewModel: LoginViewModel = hiltViewModel(),
    redditViewModel: RedditViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val twitterAccess by loginViewModel.isAccessTokenAvailable.collectAsState()
    val redditAccess by redditViewModel.isAccessTokenAvailable.collectAsState()
    val twitterUser by loginViewModel.user.collectAsState()
    val redditUsername by redditViewModel.username.collectAsState()

    LaunchedEffect(authorizationCode) {
        if (authorizationCode != null) {
            loginViewModel.getAccessToken(authorizationCode.split("code=").last())
        }
    }

    LaunchedEffect(twitterAccess, redditAccess) {
        delay(500)
        if (twitterAccess || redditAccess) {
            Timber.d("access approved (Twitter: $twitterAccess, Reddit: $redditAccess)")
            delay(1500)
            navController.navigate(Screens.HOMESCREEN.screenRoute(true)) {
                popUpTo(Screens.LOGINSCREEN.name) { inclusive = true }
            }
        }
    }

    LoginScreen(
        uiState = LoginUiState(
            twitterConnected = twitterAccess,
            redditConnected = redditAccess,
            twitterUsername = twitterUser?.username.orEmpty(),
            twitterDisplayName = twitterUser?.name.orEmpty(),
            twitterAvatarUrl = twitterUser?.profileImageUrl.orEmpty(),
            redditUsername = redditUsername,
            isProcessingCallback = authorizationCode != null && !twitterAccess && !redditAccess,
            isDebug = BuildConfig.DEBUG,
        ),
        onConnectTwitter = { context.startActivity(loginViewModel.authIntent()) },
        onConnectReddit = { context.startActivity(redditViewModel.authIntent()) },
        onSkipAuth = {
            navController.navigate(Screens.HOMESCREEN.screenRoute(false)) {
                popUpTo(Screens.LOGINSCREEN.name) { inclusive = true }
            }
        },
    )
}
