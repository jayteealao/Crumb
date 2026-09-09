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

/**
 * Returns `true` only when [authState] indicates a successful Firebase session,
 * making [AuthUiState.Authenticated] the sole trigger for auto-navigation past
 * the login screen. Every other state — including [AuthUiState.SigningIn] — is an
 * explicit no-op so that an in-flight Credential Manager coroutine can never be
 * cancelled by a navigation side effect.
 *
 * Pure function: unit-testable without a Compose host (see [LoginAutoNavTest]).
 */
fun shouldAutoNavigate(authState: AuthUiState): Boolean =
    when (authState) {
        is AuthUiState.Authenticated -> true

        AuthUiState.SigningIn,
        AuthUiState.SignedOut,
        is AuthUiState.CollisionRequiresEmailLink,
        AuthUiState.EmailPasswordEntry,
        is AuthUiState.Error,
        -> false
    }

/**
 * Navigation entry point for the login destination. Wires ViewModels into [LoginScreen] and
 * handles auto-navigation to Home once Firebase Auth reports a successful session.
 *
 * Auto-navigation is keyed solely on [AuthUiState.Authenticated] — legacy Twitter/Reddit
 * token availability no longer triggers navigation. This prevents a stale on-device token
 * from racing an in-flight Credential Manager Google sign-in and cancelling the sheet.
 *
 * @param navController Used to navigate forward to Home or ConnectX after authentication.
 * @param authorizationCode OAuth code forwarded from the deep-link intent; triggers Twitter token exchange.
 * @param loginViewModel Provides Twitter user info, the token-exchange call, and logout.
 * @param redditViewModel Provides Reddit access-token availability and the auth intent.
 * @param authViewModel Manages Firebase sign-in state (Google, email/password, and sign-out).
 */
@Composable
fun LoginRoute(
    navController: NavController,
    authorizationCode: String? = null,
    loginViewModel: LoginViewModel = hiltViewModel(),
    redditViewModel: RedditViewModel = hiltViewModel(),
    authViewModel: FirebaseAuthViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val redditAccess by redditViewModel.isAccessTokenAvailable.collectAsState()
    val twitterUser by loginViewModel.user.collectAsState()
    val redditUsername by redditViewModel.username.collectAsState()
    val authState by authViewModel.uiState.collectAsState()

    LaunchedEffect(authorizationCode) {
        if (authorizationCode != null) {
            loginViewModel.getAccessToken(authorizationCode.split("code=").last())
        }
    }

    // Auto-route past LoginScreen once Firebase reports an authenticated user.
    // shouldAutoNavigate is exhaustive over AuthUiState: SigningIn and every
    // non-Authenticated state are explicit false branches, ensuring no navigation
    // fires while the Credential Manager sheet is open.
    LaunchedEffect(authState) {
        if (shouldAutoNavigate(authState)) {
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
        uiState =
            LoginUiState(
                // Legacy Twitter token chip is retired post-cutover; hardwired false so
                // a stale on-device Prefs token is never reflected in the UI.
                twitterConnected = false,
                redditConnected = redditAccess,
                twitterUsername = twitterUser?.username.orEmpty(),
                twitterDisplayName = twitterUser?.name.orEmpty(),
                twitterAvatarUrl = twitterUser?.profileImageUrl.orEmpty(),
                redditUsername = redditUsername,
                // isProcessingCallback guards the Twitter OAuth callback path; no
                // longer conditioned on twitterAccess since the token signal is retired.
                isProcessingCallback = authorizationCode != null && !redditAccess,
                isDebug = BuildConfig.DEBUG,
                firebaseSignedIn = signedIn,
                firebaseSigningIn = signingIn,
                collisionPromptVisible = collisionVisible,
                emailDialogVisible = emailEntryVisible,
                authErrorMessage = errorMessage,
            ),
        onConnectTwitter = {
            // Route X-OAuth through the dedicated Connect-X destination, which
            // owns the Custom Tabs + deep-link round-trip via
            // [TwitterOAuthCoordinator]. The legacy on-device authIntent() is
            // kept on LoginViewModel for parity with Reddit but is no longer
            // used by the live flow.
            navController.navigate(Screens.CONNECTX.name)
        },
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
