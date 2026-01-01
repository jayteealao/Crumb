package com.github.jayteealao.crumbs.screens.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
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
            .padding(horizontal = 32.dp),
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
                    text = "Welcome back",
                    style = MaterialTheme.typography.subtitle1,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = user?.name ?: "",
                    style = MaterialTheme.typography.h6
                )

                Text(
                    text = "@${user?.username ?: ""}",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }

            // Loading state - processing authorization
            authorizationCode != null -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 4.dp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Connecting to X...",
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }

            // Initial state - prompt to login
            else -> {
                Text(
                    text = "Crumbs",
                    style = MaterialTheme.typography.h4
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Save your bookmarks",
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        context.startActivity(viewModel.authIntent())
                    },
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary
                    )
                ) {
                    Text(
                        text = "Connect with X",
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        }
    }
}
