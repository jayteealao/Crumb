package com.github.jayteealao.crumbs.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.github.jayteealao.crumbs.designsystem.components.BottomNavTab
import com.github.jayteealao.crumbs.designsystem.components.CrumbsBottomNav
import com.github.jayteealao.crumbs.designsystem.components.CrumbsScaffold
import com.github.jayteealao.crumbs.designsystem.components.CrumbsTopBar
import com.github.jayteealao.reddit.screens.RedditBookmarksScreen
import com.github.jayteealao.twitter.screens.LoginViewModel
import com.github.jayteealao.twitter.screens.BookmarksViewModel
import com.github.jayteealao.twitter.screens.TwitterBookmarksScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    loginViewModel: LoginViewModel,
    bookmarksViewModel: BookmarksViewModel,
    twitterAuthCode: String = ""
){
    var selectedTab by remember { mutableStateOf(BottomNavTab.TWITTER) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    CrumbsScaffold(
        topBar = {
            CrumbsTopBar(
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onSearchActiveChange = { isSearchActive = it }
            )
        },
        bottomBar = {
            CrumbsBottomNav(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                BottomNavTab.TWITTER -> {
                    TwitterBookmarksScreen(
                        navController = navController,
                        twitterAuthCode = twitterAuthCode,
                        bookmarksViewModel = bookmarksViewModel,
                        loginViewModel = loginViewModel
                    )
                }

                BottomNavTab.REDDIT -> {
                    RedditBookmarksScreen(
                        navController = navController,
                        bookmarksViewModel = bookmarksViewModel
                    )
                }

                BottomNavTab.ALL -> {
                    AllBookmarksScreen(
                        loginViewModel = loginViewModel,
                        bookmarksViewModel = bookmarksViewModel
                    )
                }

                BottomNavTab.MAP -> {
                    MapViewScreen()
                }
            }
        }
    }
}