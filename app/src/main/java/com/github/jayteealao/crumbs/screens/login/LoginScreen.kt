package com.github.jayteealao.crumbs.screens.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import coil.size.Size
import com.github.jayteealao.crumbs.R
import com.github.jayteealao.crumbs.Screens
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

    if (authorizationCode == null) {
        Column(modifier = modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.logo_2),
                contentDescription = "",
                modifier = Modifier.size(400.dp)
            )
            Text(text = "Sign in to twitter to enable app functionality")
            TextButton(
                onClick = {
                    context.startActivity(
                        viewModel.authIntent()
                    )
                }
            ) {
                Text(text = "Login To Twitter")
            }
        }
    } else if (user != null && access && authorizationCode.isNotBlank()) {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(user?.profileImageUrl)
                    .size(Size.ORIGINAL)
                    .scale(Scale.FILL)
                    .crossfade(true)
                    .build(),
                contentDescription = "",
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 8.dp)
                    .size(100.dp)
                    .clip(
                        CircleShape
                    )
            )
            Text(text = user?.name ?: "")
            Text(text = user?.username ?: "")
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "Authorization Loading")
        }
    }
}
