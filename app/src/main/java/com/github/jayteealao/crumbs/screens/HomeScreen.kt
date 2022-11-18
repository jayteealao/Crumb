package com.github.jayteealao.crumbs.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.jayteealao.crumbs.screens.login.LoginViewModel
import com.github.jayteealao.crumbs.screens.twitter.BookmarksViewModel
import com.github.jayteealao.crumbs.screens.twitter.TwitterBookmarksScreen
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.pagerTabIndicatorOffset
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
){
    val pagerState = rememberPagerState()
    val pages: List<String> = remember {
        mutableStateListOf("twitter", "reddit")
    }
    TabRow(
        selectedTabIndex = pagerState.currentPage,
        indicator = { tabPositions ->
            TabRowDefaults.Indicator(
                Modifier.pagerTabIndicatorOffset(pagerState, tabPositions)
            )
        }
    ) {
        pages.forEachIndexed { index, title ->
            Tab(
                selected = pagerState.currentPage == index,
                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                modifier = Modifier.height(32.dp)
            ) {
                Text(text = title)
            }
        }
    }
    HorizontalPager(
        count = pages.size,
        state = pagerState,
        contentPadding = PaddingValues(top = 32.dp)
    ) { page ->
        when (page) {
            0 -> {
                TwitterBookmarksScreen(
                    navController,
                    twitterAuthCode = twitterAuthCode,
                    bookmarksViewModel = bookmarksViewModel,
                    loginViewModel = loginViewModel
                )
            }

            1 -> Box(modifier = Modifier.fillMaxSize())
        }
    }
}