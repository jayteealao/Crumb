package com.github.jayteealao.crumbs.screens.login

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.github.jayteealao.crumbs.BuildConfig
import com.github.jayteealao.crumbs.Screens
import com.github.jayteealao.crumbs.auth.AuthUiState
import com.github.jayteealao.crumbs.auth.FirebaseAuthViewModel
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
    authViewModel: FirebaseAuthViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val twitterAccess by loginViewModel.isAccessTokenAvailable.collectAsState()
    val redditAccess by redditViewModel.isAccessTokenAvailable.collectAsState()
    val twitterUser by loginViewModel.user.collectAsState()
    val redditUsername by redditViewModel.username.collectAsState()
    val authState by authViewModel.uiState.collectAsState()

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

    // Auto-route past LoginScreen once Firebase reports an authenticated user.
    // The wrong-account guard lives function-side (Firestore allowlist); the
    // app never inspects the UID locally.
    LaunchedEffect(authState) {
        if (authState is AuthUiState.Authenticated) {
            navController.navigate(Screens.HOMESCREEN.screenRoute(true)) {
                popUpTo(Screens.LOGINSCREEN.name) { inclusive = true }
            }
        }
    }

    val collisionVisible = authState is AuthUiState.CollisionRequiresEmailLink
    val emailEntryVisible = authState is AuthUiState.EmailPasswordEntry
    val signingIn = authState is AuthUiState.SigningIn
    val signedIn = authState is AuthUiState.Authenticated
    val errorMessage = (authState as? AuthUiState.Error)?.reason

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
            firebaseSignedIn = signedIn,
            firebaseSigningIn = signingIn,
            collisionPromptVisible = collisionVisible,
            emailDialogVisible = emailEntryVisible,
            authErrorMessage = errorMessage,
        ),
        onConnectTwitter = { context.startActivity(loginViewModel.authIntent()) },
        onConnectReddit = { context.startActivity(redditViewModel.authIntent()) },
        onSkipAuth = {
            navController.navigate(Screens.HOMESCREEN.screenRoute(false)) {
                popUpTo(Screens.LOGINSCREEN.name) { inclusive = true }
            }
        },
        onLogoutTwitter = { loginViewModel.logout() },
        onLogoutReddit = { redditViewModel.logout() },
        onSignInWithGoogle = {
            // Credential Manager needs an Activity context for the OS sheet.
            (context as? Activity)?.let(authViewModel::onGoogleSignInClicked)
        },
        onSignInWithEmail = { authViewModel.onSignInWithEmailClicked() },
        onEmailPasswordSubmit = authViewModel::onEmailPasswordSubmit,
        onDismissAuthDialog = { authViewModel.onDismissCollision() },
        onSignOutFirebase = { authViewModel.onSignOut() },
    )
}
