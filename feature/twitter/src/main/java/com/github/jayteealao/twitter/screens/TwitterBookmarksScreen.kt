package com.github.jayteealao.twitter.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.items
import com.github.jayteealao.twitter.components.TwitterCard
import timber.log.Timber

@Composable
fun TwitterBookmarksScreen(
    navController: NavController,
    twitterAuthCode: String? = "",
    bookmarksViewModel: BookmarksViewModel = hiltViewModel(),
    loginViewModel: LoginViewModel = hiltViewModel()
) {
    val pagedBookmarks = bookmarksViewModel.pagingFlowData().collectAsLazyPagingItems()
    val refreshed = loginViewModel.refreshedTokens
    val loggedIn by loginViewModel.isAccessTokenAvailable.collectAsState()
    val hasTwitterAuthCode = !twitterAuthCode.isNullOrBlank()

    LaunchedEffect(loggedIn) {
        Timber.d("refreshed:$refreshed, loggedIn:$loggedIn, hasTwitterAuthCode:$hasTwitterAuthCode" )
        // Trigger bookmark sync when user successfully logs in
        if (loggedIn) {
            Timber.d("Triggering buildDatabase after login")
            bookmarksViewModel.buildDatabase()
        }
    }

    LaunchedEffect(true) {
        if (!twitterAuthCode.isNullOrBlank()) {
            loginViewModel.getAccessToken(twitterAuthCode.split("code=").last())
        }
    }

    when {
        !loggedIn -> {
            LinkTwitter(intentFn = loginViewModel.authIntent())
        }

        else -> {
            LazyColumn(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                item {
                    Text("Bookmarks")
                }
                item {
                    Text(refreshed.toString())
                }
                items(
                    pagedBookmarks,
                    key = { it.tweet.id }
                ) {
                    if (it != null) {
                        TwitterCard(it)
                        Divider(
                            thickness = 1.dp,
                            modifier = Modifier
                                .background(
                                    brush = Brush.horizontalGradient(
                                        listOf(Color(0x80F12711), Color(0x80F5AF19))
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LinkTwitter(intentFn: Intent) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Sign in to twitter to enable app functionality")
        TextButton(
            onClick = {
                context.startActivity(
                    intentFn
//                    viewModel.authIntent()
                )
            },
            colors = ButtonDefaults.textButtonColors(
                contentColor = Color.Blue
            )
        ) {
            Text(text = "Login To Twitter")
        }
    }
}
