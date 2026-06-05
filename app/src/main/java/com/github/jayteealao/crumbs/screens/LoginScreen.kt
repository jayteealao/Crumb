package com.github.jayteealao.crumbs.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import com.github.jayteealao.crumbs.designsystem.components.ButtonStyle
import com.github.jayteealao.crumbs.designsystem.components.CrumbsButton
import com.github.jayteealao.crumbs.designsystem.components.HatchedScrim
import com.github.jayteealao.crumbs.designsystem.components.ProfileSize
import com.github.jayteealao.crumbs.designsystem.components.UserProfile
import com.github.jayteealao.crumbs.designsystem.components.UserProfileDisplay
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography
import com.github.jayteealao.crumbs.models.BookmarkSource

@androidx.compose.runtime.Immutable
data class LoginUiState(
    val twitterConnected: Boolean = false,
    val redditConnected: Boolean = false,
    val twitterUsername: String = "",
    val twitterDisplayName: String = "",
    val twitterAvatarUrl: String = "",
    val redditUsername: String = "",
    val isProcessingCallback: Boolean = false,
    val isDebug: Boolean = false,
    // Firebase auth surface — primary CTA on this screen.
    val firebaseSignedIn: Boolean = false,
    val firebaseSigningIn: Boolean = false,
    val collisionPromptVisible: Boolean = false,
    val emailDialogVisible: Boolean = false,
    val authErrorMessage: String? = null,
)

/**
 * Account-connection screen shown before the user has completed authentication. Offers Firebase
 * sign-in (Google or email/password) alongside Twitter and Reddit OAuth buttons, and surfaces
 * an inline email/password dialog when needed.
 *
 * @param uiState Snapshot of connection state, Firebase auth progress, and dialog visibility flags.
 * @param onConnectTwitter Called when the user taps the Twitter connect button; navigates to ConnectX.
 * @param onConnectReddit Called when the user taps the Reddit connect button; launches the OAuth intent.
 * @param onSkipAuth Called when the user taps "Skip Auth (Debug)"; only visible in debug builds.
 * @param onLogoutTwitter Called when the user taps the logout button on a connected Twitter profile.
 * @param onLogoutReddit Called when the user taps the logout button on a connected Reddit profile.
 * @param onSignInWithGoogle Called to launch the Credential Manager Google sign-in sheet.
 * @param onSignInWithEmail Called to show the email/password entry dialog.
 * @param onEmailPasswordSubmit Called with the entered email and password when the dialog is submitted.
 * @param onDismissAuthDialog Called when the email/password dialog is dismissed.
 * @param onSignOutFirebase Called when the user taps "Sign Out" while Firebase-authenticated.
 */
@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onConnectTwitter: () -> Unit,
    onConnectReddit: () -> Unit,
    onSkipAuth: () -> Unit,
    onLogoutTwitter: () -> Unit = {},
    onLogoutReddit: () -> Unit = {},
    onSignInWithGoogle: () -> Unit = {},
    onSignInWithEmail: () -> Unit = {},
    onEmailPasswordSubmit: (email: String, password: String) -> Unit = { _, _ -> },
    onDismissAuthDialog: () -> Unit = {},
    onSignOutFirebase: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val typography = LocalCrumbsTypography.current

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = spacing.xl)
                .testTag("login-screen"),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "WELCOME",
                style = typography.captionMono,
                color = colors.accent,
                modifier = Modifier.testTag("login-kicker"),
            )
            Spacer(modifier = Modifier.height(spacing.md))
            Text(
                text = "crumbs•",
                style = typography.displayHeadline,
                color = colors.ink,
                modifier = Modifier.testTag("login-wordmark"),
            )
            Spacer(modifier = Modifier.height(spacing.lg))
            Text(
                text = "CONNECT YOUR ACCOUNTS TO START SAVING CRUMBS.",
                style = typography.bodyMono,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
            Spacer(modifier = Modifier.height(spacing.xxl))

            // Firebase row — Google primary CTA, then "sign in with email" link.
            if (uiState.firebaseSignedIn) {
                CrumbsButton(
                    onClick = onSignOutFirebase,
                    text = "SIGN OUT",
                    style = ButtonStyle.Secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login-firebase-signout"),
                )
            } else {
                CrumbsButton(
                    onClick = onSignInWithGoogle,
                    text = if (uiState.firebaseSigningIn) "SIGNING IN…" else "CONTINUE WITH GOOGLE",
                    style = ButtonStyle.Primary,
                    enabled = !uiState.firebaseSigningIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login-google-cta"),
                )
                Spacer(modifier = Modifier.height(spacing.sm))
                Text(
                    text = "SIGN IN WITH EMAIL INSTEAD",
                    style = typography.captionMono.copy(textDecoration = TextDecoration.Underline),
                    color = colors.ink,
                    modifier = Modifier
                        .testTag("login-email-link")
                        .clickable(enabled = !uiState.firebaseSigningIn) { onSignInWithEmail() }
                        .padding(vertical = spacing.xs),
                )
            }

            uiState.authErrorMessage?.let { message ->
                Spacer(modifier = Modifier.height(spacing.sm))
                Text(
                    text = message.uppercase(),
                    style = typography.captionMono,
                    color = colors.accent,
                    modifier = Modifier.testTag("login-auth-error"),
                )
            }

            Spacer(modifier = Modifier.height(spacing.xl))

            // Twitter row — unchanged from prior brutalist surface.
            if (uiState.twitterConnected && uiState.twitterUsername.isNotBlank()) {
                UserProfileDisplay(
                    profile = UserProfile(
                        username = uiState.twitterUsername,
                        displayName = uiState.twitterDisplayName.ifBlank { "@${uiState.twitterUsername}" },
                        avatarUrl = uiState.twitterAvatarUrl,
                        source = BookmarkSource.Twitter,
                    ),
                    size = ProfileSize.Medium,
                    modifier = Modifier.testTag("login-twitter-profile"),
                )
                Spacer(modifier = Modifier.height(spacing.sm))
                CrumbsButton(
                    onClick = onLogoutTwitter,
                    text = "LOGOUT TWITTER",
                    style = ButtonStyle.Secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login-twitter-logout"),
                )
            } else {
                CrumbsButton(
                    onClick = onConnectTwitter,
                    text = "CONNECT TWITTER",
                    style = ButtonStyle.Secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login-twitter-cta"),
                )
            }
            Spacer(modifier = Modifier.height(spacing.md))

            // Reddit row — unchanged.
            if (uiState.redditConnected && uiState.redditUsername.isNotBlank()) {
                UserProfileDisplay(
                    profile = UserProfile(
                        username = uiState.redditUsername,
                        displayName = "u/${uiState.redditUsername}",
                        avatarUrl = "",
                        source = BookmarkSource.Reddit,
                    ),
                    size = ProfileSize.Medium,
                    modifier = Modifier.testTag("login-reddit-profile"),
                )
                Spacer(modifier = Modifier.height(spacing.sm))
                CrumbsButton(
                    onClick = onLogoutReddit,
                    text = "LOGOUT REDDIT",
                    style = ButtonStyle.Secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login-reddit-logout"),
                )
            } else {
                CrumbsButton(
                    onClick = onConnectReddit,
                    text = "CONNECT REDDIT",
                    style = ButtonStyle.Secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login-reddit-cta"),
                )
            }

            if (uiState.isDebug) {
                Spacer(modifier = Modifier.height(spacing.md))
                CrumbsButton(
                    onClick = onSkipAuth,
                    text = "SKIP AUTH (DEBUG)",
                    style = ButtonStyle.Secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login-skip-auth"),
                )
            }
        }

        if (uiState.collisionPromptVisible || uiState.emailDialogVisible) {
            EmailPasswordSignInDialog(
                isCollision = uiState.collisionPromptVisible,
                onSubmit = onEmailPasswordSubmit,
                onDismiss = onDismissAuthDialog,
            )
        }
    }
}

@Composable
private fun EmailPasswordSignInDialog(
    isCollision: Boolean,
    onSubmit: (email: String, password: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val stroke = LocalCrumbsStroke.current
    val typography = LocalCrumbsTypography.current

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Tap-outside-to-dismiss; matches the brutalist no-chrome aesthetic.
            .semantics { role = Role.Button; contentDescription = "Dismiss dialog" }
            .clickable(onClickLabel = "Dismiss", onClick = onDismiss)
            .testTag("login-email-dialog-scrim"),
        contentAlignment = Alignment.Center,
    ) {
        // Hatched-scrim brush replaces the plain alpha-60 black backdrop so
        // the email-dialog backdrop matches the rest of the design system
        // (OverlayShell + filter overlay both use the same 32% alpha hatch).
        HatchedScrim(
            modifier = Modifier.fillMaxSize(),
            baseAlpha = 0.32f,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .background(colors.surface)
                .border(stroke.regular, colors.ink)
                .padding(spacing.lg)
                // Swallow scrim taps inside the card so typing doesn't dismiss.
                .clickable(enabled = false) {}
                .testTag("login-email-dialog"),
        ) {
            Text(
                text = if (isCollision) "EXISTING ACCOUNT FOUND" else "SIGN IN WITH EMAIL",
                style = typography.captionMono,
                color = colors.accent,
            )
            Spacer(modifier = Modifier.height(spacing.xs))
            Text(
                text = if (isCollision) {
                    "AN ACCOUNT WITH THIS EMAIL ALREADY EXISTS.\nSIGN IN WITH YOUR PASSWORD TO CONNECT YOUR GOOGLE ACCOUNT."
                } else {
                    "ENTER YOUR EMAIL AND PASSWORD."
                },
                style = typography.bodyMono,
                color = colors.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(spacing.lg))

            BasicTextField(
                value = email,
                onValueChange = { email = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                textStyle = typography.bodyMono.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.ink),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(stroke.regular, colors.ink)
                    .padding(horizontal = spacing.sm, vertical = spacing.sm)
                    .semantics { contentDescription = "Email" }
                    .testTag("login-email-field"),
            )
            Spacer(modifier = Modifier.height(spacing.sm))
            BasicTextField(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                textStyle = typography.bodyMono.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.ink),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(stroke.regular, colors.ink)
                    .padding(horizontal = spacing.sm, vertical = spacing.sm)
                    .semantics { contentDescription = "Password" }
                    .testTag("login-password-field"),
            )

            Spacer(modifier = Modifier.height(spacing.lg))
            CrumbsButton(
                onClick = { onSubmit(email, password) },
                text = if (isCollision) "SIGN IN & LINK" else "SIGN IN",
                style = ButtonStyle.Primary,
                enabled = email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login-email-submit"),
            )
            Spacer(modifier = Modifier.height(spacing.sm))
            Text(
                text = "CANCEL",
                style = typography.captionMono.copy(textDecoration = TextDecoration.Underline),
                color = colors.ink,
                modifier = Modifier
                    .testTag("login-email-cancel")
                    .semantics { role = Role.Button }
                    .clickable(onClickLabel = "Cancel", onClick = onDismiss)
                    .padding(vertical = spacing.xs),
            )
        }
    }
}

@Preview(name = "Login Light", showBackground = true)
@Composable
private fun PreviewLoginLight() {
    CrumbsTheme(darkTheme = false) {
        LoginScreen(
            uiState = LoginUiState(isDebug = true),
            onConnectTwitter = {},
            onConnectReddit = {},
            onSkipAuth = {},
        )
    }
}

@Preview(name = "Login Dark", showBackground = true)
@Composable
private fun PreviewLoginDark() {
    CrumbsTheme(darkTheme = true) {
        LoginScreen(
            uiState = LoginUiState(
                twitterConnected = true,
                twitterUsername = "design",
                twitterDisplayName = "@design",
                redditConnected = false,
            ),
            onConnectTwitter = {},
            onConnectReddit = {},
            onSkipAuth = {},
        )
    }
}
