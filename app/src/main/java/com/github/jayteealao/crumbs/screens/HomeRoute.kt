package com.github.jayteealao.crumbs.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.github.jayteealao.crumbs.designsystem.components.BottomNavTab
import com.github.jayteealao.reddit.screens.RedditBookmarksRoute
import com.github.jayteealao.twitter.screens.BookmarksViewModel
import com.github.jayteealao.twitter.screens.LoginViewModel
import com.github.jayteealao.twitter.screens.TwitterBookmarksRoute

@Composable
fun HomeRoute(
    navController: NavController,
    twitterAuthCode: String = "",
    loginViewModel: LoginViewModel = hiltViewModel(),
    bookmarksViewModel: BookmarksViewModel = hiltViewModel(),
) {
    var selectedTab by remember { mutableStateOf(BottomNavTab.TWITTER) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    HomeScreen(
        uiState = HomeUiState(
            selectedTab = selectedTab,
            isSearchActive = isSearchActive,
            searchQuery = searchQuery,
        ),
        onTabSelected = { selectedTab = it },
        onSearchQueryChange = { searchQuery = it },
        onSearchActiveChange = { isSearchActive = it },
    ) { tab, padding ->
        when (tab) {
            BottomNavTab.TWITTER -> TwitterBookmarksRoute(
                navController = navController,
                contentPadding = padding,
                twitterAuthCode = twitterAuthCode,
                bookmarksViewModel = bookmarksViewModel,
                loginViewModel = loginViewModel,
            )
            BottomNavTab.REDDIT -> RedditBookmarksRoute(
                navController = navController,
                contentPadding = padding,
                bookmarksViewModel = bookmarksViewModel,
            )
            BottomNavTab.ALL -> AllBookmarksRoute(
                contentPadding = padding,
                loginViewModel = loginViewModel,
                bookmarksViewModel = bookmarksViewModel,
            )
            BottomNavTab.MAP -> MapViewRoute(contentPadding = padding)
        }
    }
}
