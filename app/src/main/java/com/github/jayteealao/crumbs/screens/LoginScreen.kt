package com.github.jayteealao.crumbs.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.github.jayteealao.crumbs.designsystem.components.ButtonStyle
import com.github.jayteealao.crumbs.designsystem.components.CrumbsButton
import com.github.jayteealao.crumbs.designsystem.components.ProfileSize
import com.github.jayteealao.crumbs.designsystem.components.UserProfile
import com.github.jayteealao.crumbs.designsystem.components.UserProfileDisplay
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
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
)

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onConnectTwitter: () -> Unit,
    onConnectReddit: () -> Unit,
    onSkipAuth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val typography = LocalCrumbsTypography.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
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

        // Twitter row
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
        } else {
            CrumbsButton(
                onClick = onConnectTwitter,
                text = "CONNECT TWITTER",
                style = ButtonStyle.Primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login-twitter-cta"),
            )
        }
        Spacer(modifier = Modifier.height(spacing.md))

        // Reddit row
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
