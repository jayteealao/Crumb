package com.github.jayteealao.crumbs.screens.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import coil.size.Size
import com.github.jayteealao.crumbs.R
import com.github.jayteealao.crumbs.Screens
import com.github.jayteealao.crumbs.designsystem.components.DudsLoadingIndicator
import com.github.jayteealao.crumbs.designsystem.components.DudsPrimaryButton
import com.github.jayteealao.crumbs.designsystem.theme.DudsColors
import com.github.jayteealao.crumbs.designsystem.theme.DudsTypography
import com.github.jayteealao.twitter.screens.LoginViewModel
import kotlinx.coroutines.delay
import timber.log.Timber

@Composable
fun LoginScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
    authorizationCode: String? = null
) {
    val context = LocalContext.current
    val access by viewModel.isAccessTokenAvailable.collectAsState()
    val user by viewModel.user.collectAsState()

    LaunchedEffect(authorizationCode) {
        if (authorizationCode != null) {
            viewModel.getAccessToken(authorizationCode.split("code=").last())
        }
    }

    LaunchedEffect(access) {
        delay(500)
        if (access) {
            Timber.d("access approved")
            delay(1500)
            navController.navigate(Screens.HOMESCREEN.screenRoute(true)) {
                popUpTo(Screens.LOGINSCREEN.name) { inclusive = true }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DudsColors.backgroundGradient)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo - shown in all states
        Image(
            painter = painterResource(id = R.drawable.logo_2),
            contentDescription = "Crumbs logo",
            modifier = Modifier.size(120.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        when {
            // Success state - user authenticated
            user != null && access && authorizationCode?.isNotBlank() == true -> {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(user?.profileImageUrl)
                        .size(Size.ORIGINAL)
                        .scale(Scale.FILL)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Profile picture",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "welcome back",
                    style = DudsTypography.bodySecondary,
                    color = DudsColors.textSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = user?.name ?: "",
                    style = DudsTypography.h2CardTitle
                )

                Text(
                    text = "@${user?.username ?: ""}",
                    style = DudsTypography.bodySecondary,
                    color = DudsColors.textSecondary
                )
            }

            // Loading state - processing authorization
            authorizationCode != null -> {
                DudsLoadingIndicator()

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "connecting to x...",
                    style = DudsTypography.bodyPrimary,
                    color = DudsColors.textSecondary
                )
            }

            // Initial state - prompt to login
            else -> {
                Text(
                    text = "CRUMBS",
                    style = DudsTypography.h1Section
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "your daily dose of saved bookmarks",
                    style = DudsTypography.bodyPrimary,
                    color = DudsColors.textSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                DudsPrimaryButton(
                    text = "connect with x",
                    onClick = {
                        context.startActivity(viewModel.authIntent())
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
