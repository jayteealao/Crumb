package com.github.jayteealao.crumbs.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.jayteealao.crumbs.Screens
import com.github.jayteealao.crumbs.designsystem.components.CrumbsButton
import com.github.jayteealao.crumbs.designsystem.components.CrumbsIconButton
import com.github.jayteealao.crumbs.designsystem.components.CrumbsScaffold
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.HorizontalPagerIndicator
import com.google.accompanist.pager.rememberPagerState
import kotlinx.coroutines.launch

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@OptIn(ExperimentalPagerApi::class)
@Composable
fun OnboardingScreen(
    navController: NavController
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val typography = LocalCrumbsTypography.current
    val pagerState = rememberPagerState()
    val scope = rememberCoroutineScope()

    val pages = listOf(
        OnboardingPage(
            title = "Leave breadcrumbs",
            description = "Save social content worth remembering from Twitter and Reddit",
            icon = Icons.Default.Bookmark
        ),
        OnboardingPage(
            title = "Find your way back",
            description = "Search and filter through your saved bookmarks instantly",
            icon = Icons.Default.Search
        ),
        OnboardingPage(
            title = "Create your knowledge base",
            description = "Organize bookmarks with tags for easy discovery",
            icon = Icons.Default.Tag
        ),
        OnboardingPage(
            title = "Discover connections",
            description = "Visualize relationships between your bookmarks",
            icon = Icons.Default.Map
        )
    )

    CrumbsScaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Skip button
            CrumbsIconButton(
                onClick = {
                    navController.navigate(Screens.LOGINSCREEN.name) {
                        popUpTo(Screens.SPLASHSCREEN.name) { inclusive = true }
                    }
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Skip"
                    )
                },
                contentDescription = "Skip",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(spacing.lg)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.height(spacing.xxl))

                // Pager
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    HorizontalPager(
                        count = pages.size,
                        state = pagerState,
                        modifier = Modifier.weight(1f)
                    ) { page ->
                        OnboardingPageContent(pages[page])
                    }

                    Spacer(modifier = Modifier.height(spacing.xl))

                    // Page indicator
                    HorizontalPagerIndicator(
                        pagerState = pagerState,
                        activeColor = colors.accent,
                        inactiveColor = colors.textSecondary.copy(alpha = 0.3f),
                        indicatorWidth = 8.dp,
                        indicatorHeight = 8.dp,
                        spacing = 8.dp
                    )
                }

                Spacer(modifier = Modifier.height(spacing.xl))

                // Bottom buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.md)
                ) {
                    if (pagerState.currentPage < pages.size - 1) {
                        CrumbsButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            },
                            text = "Next",
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        CrumbsButton(
                            onClick = {
                                navController.navigate(Screens.LOGINSCREEN.name) {
                                    popUpTo(Screens.SPLASHSCREEN.name) { inclusive = true }
                                }
                            },
                            text = "Get Started",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val typography = LocalCrumbsTypography.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = page.icon,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = colors.accent
        )

        Spacer(modifier = Modifier.height(spacing.xxl))

        Text(
            text = page.title,
            style = typography.displayMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(spacing.lg))

        Text(
            text = page.description,
            style = typography.bodyLarge,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}
