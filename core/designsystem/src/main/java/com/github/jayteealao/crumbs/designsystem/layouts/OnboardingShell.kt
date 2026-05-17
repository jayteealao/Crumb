package com.github.jayteealao.crumbs.designsystem.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.components.ButtonStyle
import com.github.jayteealao.crumbs.designsystem.components.CrumbsButton
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

// Brutalist full-bleed onboarding/login shell. Internally composes the
// Compose-native HorizontalPager. The pager's content is supplied as an
// ImmutableList of @Composable () -> Unit pages so callers cannot accidentally
// mutate the list and trigger over-recomposition.
//
// Footer is a single Row(SpaceBetween) with a shell-owned 3-pill page
// indicator on the left and an optional CrumbsButton CTA on the right.

@Composable
fun OnboardingShell(
    pages: ImmutableList<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    pagerState: PagerState = rememberPagerState(pageCount = { pages.size }),
    header: (@Composable () -> Unit)? = null,
    footerCtaText: String? = null,
    onFooterCtaClick: (() -> Unit)? = null,
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current

    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("onboarding-shell")
    ) {
        if (header != null) {
            header()
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("onboarding-shell-pager"),
        ) { page ->
            pages[page]()
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.lg)
                .testTag("onboarding-shell-footer"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OnboardingPageIndicator(pagerState = pagerState)
            if (footerCtaText != null && onFooterCtaClick != null) {
                CrumbsButton(
                    onClick = onFooterCtaClick,
                    text = footerCtaText,
                    style = ButtonStyle.Primary,
                )
            }
        }
    }
}

@Composable
internal fun OnboardingPageIndicator(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCrumbsColors.current
    Row(
        modifier = modifier.testTag("onboarding-shell-indicator"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pagerState.pageCount) { index ->
            val isCurrent = index == pagerState.currentPage
            Box(
                Modifier
                    .width(14.dp)
                    .height(4.dp)
                    .background(if (isCurrent) colors.accent else colors.ink.copy(alpha = 0.25f))
            )
        }
    }
}

// Previews

@Composable
private fun PreviewOnboardingPage(label: String) {
    val colors = LocalCrumbsColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = colors.ink)
    }
}

@Preview(name = "OnboardingShell Page 0 Light", showBackground = true)
@Composable
private fun PreviewOnboardingLight() {
    CrumbsTheme(darkTheme = false) {
        OnboardingShell(
            pages = persistentListOf(
                { PreviewOnboardingPage("Page 0") },
                { PreviewOnboardingPage("Page 1") },
                { PreviewOnboardingPage("Page 2") },
            ),
            footerCtaText = "NEXT",
            onFooterCtaClick = {},
        )
    }
}

@Preview(name = "OnboardingShell Page 1 Dark", showBackground = true)
@Composable
private fun PreviewOnboardingDark() {
    CrumbsTheme(darkTheme = true) {
        OnboardingShell(
            pages = persistentListOf(
                { PreviewOnboardingPage("Page 0") },
                { PreviewOnboardingPage("Page 1") },
                { PreviewOnboardingPage("Page 2") },
            ),
            pagerState = rememberPagerState(pageCount = { 3 }, initialPage = 1),
            footerCtaText = "NEXT",
            onFooterCtaClick = {},
        )
    }
}
