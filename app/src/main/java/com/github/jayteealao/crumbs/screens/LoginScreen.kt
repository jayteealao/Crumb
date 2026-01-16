package com.github.jayteealao.crumbs.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.github.jayteealao.crumbs.Screens
import com.github.jayteealao.crumbs.designsystem.components.CrumbsButton
import com.github.jayteealao.crumbs.designsystem.components.CrumbsProgressIndicator
import com.github.jayteealao.crumbs.designsystem.components.GradientImage
import com.github.jayteealao.crumbs.designsystem.components.GradientDirection
import com.github.jayteealao.crumbs.designsystem.components.GradientIntensity
import com.github.jayteealao.crumbs.designsystem.components.ProgressSize
import com.github.jayteealao.crumbs.designsystem.components.UserProfileDisplay
import com.github.jayteealao.crumbs.designsystem.components.UserProfile
import com.github.jayteealao.crumbs.designsystem.components.ProfileSize
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.reddit.screens.RedditViewModel
import com.github.jayteealao.twitter.screens.LoginViewModel
import kotlinx.coroutines.delay
import timber.log.Timber

@Composable
fun LoginScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
    redditViewModel: RedditViewModel = hiltViewModel(),
    authorizationCode: String? = null
) {
    val context = LocalContext.current
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val typography = LocalCrumbsTypography.current

    val twitterAccess by viewModel.isAccessTokenAvailable.collectAsState()
    val redditAccess by redditViewModel.isAccessTokenAvailable.collectAsState()
    val user by viewModel.user.collectAsState()
    val redditUsername by redditViewModel.username.collectAsState()

    LaunchedEffect(authorizationCode) {
        if (authorizationCode != null) {
            viewModel.getAccessToken(authorizationCode.split("code=").last())
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

    Box(modifier = modifier.fillMaxSize()) {
        // Background gradient
        GradientImage(
            imageUrl = "https://via.placeholder.com/800x1200/1F2937/1F2937", // Solid color placeholder
            gradientDirection = GradientDirection.DiagonalTLBR,
            gradientIntensity = GradientIntensity.Dark,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // App branding
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = "Crumbs logo",
                    modifier = Modifier.size(120.dp),
                    tint = colors.accent
                )

                Spacer(modifier = Modifier.height(spacing.xl))

                Text(
                    text = "crumbs",
                    style = typography.displayLarge,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(spacing.sm))

                Text(
                    text = "Your social knowledge base",
                    style = typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(spacing.xxl * 2))

                when {
                    // Success state - user(s) authenticated
                    (user != null && twitterAccess) || (redditUsername.isNotBlank() && redditAccess) -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Show Twitter profile if authenticated
                            if (user != null && twitterAccess) {
                                UserProfileDisplay(
                                    profile = UserProfile(
                                        username = user?.username ?: "",
                                        displayName = user?.name ?: "",
                                        avatarUrl = user?.profileImageUrl ?: "",
                                        source = BookmarkSource.Twitter
                                    ),
                                    size = ProfileSize.Large
                                )
                                Spacer(modifier = Modifier.height(spacing.md))
                            }

                            // Show Reddit profile if authenticated
                            if (redditUsername.isNotBlank() && redditAccess) {
                                UserProfileDisplay(
                                    profile = UserProfile(
                                        username = redditUsername,
                                        displayName = "u/$redditUsername",
                                        avatarUrl = "",
                                        source = BookmarkSource.Reddit
                                    ),
                                    size = ProfileSize.Large
                                )
                                Spacer(modifier = Modifier.height(spacing.md))
                            }

                            Text(
                                text = "Welcome back!",
                                style = typography.headingMedium,
                                color = Color.White
                            )
                        }
                    }

                    // Loading state - processing authorization
                    authorizationCode != null -> {
                        CrumbsProgressIndicator(
                            size = ProgressSize.Large,
                            color = colors.accent
                        )

                        Spacer(modifier = Modifier.height(spacing.xl))

                        Text(
                            text = "Connecting...",
                            style = typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    // Initial state - prompt to login
                    else -> {
                        CrumbsButton(
                            onClick = {
                                context.startActivity(viewModel.authIntent())
                            },
                            text = "Connect with X",
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(spacing.md))

                        CrumbsButton(
                            onClick = {
                                context.startActivity(redditViewModel.authIntent())
                            },
                            text = "Connect with Reddit",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
