package com.github.jayteealao.crumbs.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.jayteealao.crumbs.designsystem.components.DudsBottomNav
import com.github.jayteealao.crumbs.designsystem.components.DudsChip
import com.github.jayteealao.crumbs.designsystem.components.DudsTopBar
import com.github.jayteealao.crumbs.designsystem.theme.DudsColors
import com.github.jayteealao.crumbs.designsystem.theme.DudsSpacing
import com.github.jayteealao.twitter.screens.BookmarksViewModel
import com.github.jayteealao.twitter.screens.LoginViewModel
import com.github.jayteealao.twitter.screens.TwitterBookmarksScreen
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalPagerApi::class)
@Composable
fun HomeScreen(
    scope: CoroutineScope,
    navController: NavController,
    loginViewModel: LoginViewModel,
    bookmarksViewModel: BookmarksViewModel,
    twitterAuthCode: String = ""
) {
    val pagerState = rememberPagerState()
    val pages: List<String> = remember {
        mutableStateListOf("for you", "twitter", "reddit", "following", "wishlist")
    }

    Scaffold(
        topBar = {
            DudsTopBar(
                leftText = "crumbs",
                centerIcon = Icons.Default.Star,
                rightText = "search"
            )
        },
        bottomBar = {
            DudsBottomNav(
                selectedItem = 0,
                onItemSelected = {},
                onFabClick = {}
            )
        },
        backgroundColor = DudsColors.surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DudsColors.backgroundGradient)
                .padding(paddingValues)
        ) {
            // Chip-based tab navigation
            LazyRow(
                contentPadding = PaddingValues(horizontal = DudsSpacing.base, vertical = DudsSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(DudsSpacing.sm)
            ) {
                itemsIndexed(pages) { index, title ->
                    DudsChip(
                        text = title,
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        }
                    )
                }
            }

            // Page content
            HorizontalPager(
                count = pages.size,
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0, 1 -> {
                        // For you and Twitter show bookmarks
                        TwitterBookmarksScreen(
                            navController,
                            twitterAuthCode = twitterAuthCode,
                            bookmarksViewModel = bookmarksViewModel,
                            loginViewModel = loginViewModel
                        )
                    }
                    else -> Box(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}