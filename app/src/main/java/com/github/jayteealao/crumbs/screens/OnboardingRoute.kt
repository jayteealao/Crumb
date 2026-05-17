package com.github.jayteealao.crumbs.screens

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavController
import com.github.jayteealao.crumbs.Screens
import kotlinx.coroutines.launch

@Composable
fun OnboardingRoute(
    navController: NavController,
) {
    val pages = BrutalistOnboardingPages
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    OnboardingScreen(
        pagerState = pagerState,
        onCtaClick = {
            if (pagerState.currentPage < pages.size - 1) {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            } else {
                navController.navigate(Screens.LOGINSCREEN.name) {
                    popUpTo(Screens.SPLASHSCREEN.name) { inclusive = true }
                }
            }
        },
        pages = pages,
    )
}
