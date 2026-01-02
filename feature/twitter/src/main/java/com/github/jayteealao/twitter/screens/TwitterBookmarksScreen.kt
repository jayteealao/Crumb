package com.github.jayteealao.twitter.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.items
import com.github.jayteealao.crumbs.designsystem.components.DudsPrimaryButton
import com.github.jayteealao.crumbs.designsystem.theme.DudsColors
import com.github.jayteealao.crumbs.designsystem.theme.DudsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.DudsTypography
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

    LaunchedEffect(refreshed, loggedIn, hasTwitterAuthCode) {
        Timber.d("refreshed:$refreshed, loggedIn:$loggedIn, hasTwitterAuthCode:$hasTwitterAuthCode" )
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
                contentPadding = PaddingValues(
                    horizontal = DudsSpacing.base,
                    top = DudsSpacing.base,
                    bottom = 96.dp // Extra bottom padding for bottom nav
                ),
                verticalArrangement = Arrangement.spacedBy(DudsSpacing.md),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Column {
                        Text(
                            text = "NEW IN TODAY",
                            style = DudsTypography.caption,
                            color = DudsColors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(DudsSpacing.xs))
                        Text(
                            text = "TWITTER BOOKMARKS",
                            style = DudsTypography.h1Section
                        )
                        Spacer(modifier = Modifier.height(DudsSpacing.xs))
                        Text(
                            text = "your saved tweets and threads",
                            style = DudsTypography.bodySecondary,
                            color = DudsColors.textSecondary
                        )
                    }
                }

                items(
                    pagedBookmarks,
                    key = { it.tweet.id }
                ) {
                    if (it != null) {
                        TwitterCard(it)
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
        modifier = Modifier
            .fillMaxSize()
            .background(DudsColors.backgroundGradient)
            .padding(horizontal = DudsSpacing.base),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "CONNECT TO X",
            style = DudsTypography.h1Section
        )
        Spacer(modifier = Modifier.height(DudsSpacing.sm))
        Text(
            text = "sign in to twitter to enable app functionality",
            style = DudsTypography.bodyPrimary,
            color = DudsColors.textSecondary
        )
        Spacer(modifier = Modifier.height(DudsSpacing.xl))
        DudsPrimaryButton(
            text = "connect with x",
            onClick = {
                context.startActivity(intentFn)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
