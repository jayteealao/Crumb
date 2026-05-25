package com.github.jayteealao.crumbs.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.github.jayteealao.crumbs.designsystem.layouts.OnboardingShell
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@androidx.compose.runtime.Immutable
data class OnboardingPageData(
    val kicker: String,
    val title: String,
    val body: String,
)

internal val BrutalistOnboardingPages: ImmutableList<OnboardingPageData> = persistentListOf(
    OnboardingPageData(
        kicker = "01 / 04",
        title = "LEAVE BREADCRUMBS",
        body = "SAVE SOCIAL CONTENT WORTH REMEMBERING FROM TWITTER AND REDDIT.",
    ),
    OnboardingPageData(
        kicker = "02 / 04",
        title = "FIND YOUR WAY BACK",
        body = "SEARCH AND FILTER THROUGH YOUR SAVED BOOKMARKS INSTANTLY.",
    ),
    OnboardingPageData(
        kicker = "03 / 04",
        title = "BUILD A KNOWLEDGE BASE",
        body = "ORGANIZE BOOKMARKS WITH TAGS FOR EASY DISCOVERY.",
    ),
    OnboardingPageData(
        kicker = "04 / 04",
        title = "DISCOVER CONNECTIONS",
        body = "VISUALIZE RELATIONSHIPS BETWEEN YOUR BOOKMARKS.",
    ),
)

/**
 * Multi-page onboarding carousel shown the first time the user launches the app. Each page
 * presents a kicker, headline, and body copy from [pages]; the CTA label switches from "NEXT"
 * to "GET STARTED" on the final page.
 *
 * @param pagerState Controls the current page position; caller must create via [rememberPagerState].
 * @param onCtaClick Called when the user taps the footer CTA; the route handles page advance or navigation.
 * @param pages Ordered list of page content; defaults to [BrutalistOnboardingPages].
 */
@Composable
fun OnboardingScreen(
    pagerState: PagerState,
    onCtaClick: () -> Unit,
    modifier: Modifier = Modifier,
    pages: ImmutableList<OnboardingPageData> = BrutalistOnboardingPages,
) {
    val isLastPage = pagerState.currentPage >= pages.size - 1
    OnboardingShell(
        pages = persistentListOf<@Composable () -> Unit>(
            *Array(pages.size) { idx ->
                { OnboardingPageContent(page = pages[idx], pageIndex = idx) }
            },
        ),
        pagerState = pagerState,
        footerCtaText = if (isLastPage) "GET STARTED" else "NEXT",
        onFooterCtaClick = onCtaClick,
        modifier = modifier.testTag("onboarding-screen"),
    )
}

@Composable
private fun OnboardingPageContent(page: OnboardingPageData, pageIndex: Int) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val typography = LocalCrumbsTypography.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.xl)
            .testTag("onboarding-page-$pageIndex"),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = page.kicker,
            style = typography.captionMono,
            color = colors.accent,
        )
        Spacer(modifier = Modifier.height(spacing.lg))
        Text(
            text = page.title,
            style = typography.displaySmall,
            color = colors.ink,
            textAlign = TextAlign.Start,
        )
        Spacer(modifier = Modifier.height(spacing.lg))
        Text(
            text = page.body,
            style = typography.bodyMono,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Start,
        )
    }
}

@Preview(name = "Onboarding Page 0 Light", showBackground = true)
@Composable
private fun PreviewOnboardingLight() {
    CrumbsTheme(darkTheme = false) {
        OnboardingScreen(
            pagerState = rememberPagerState(pageCount = { 4 }),
            onCtaClick = {},
        )
    }
}

@Preview(name = "Onboarding Page 3 Dark", showBackground = true)
@Composable
private fun PreviewOnboardingDark() {
    CrumbsTheme(darkTheme = true) {
        OnboardingScreen(
            pagerState = rememberPagerState(pageCount = { 4 }, initialPage = 3),
            onCtaClick = {},
        )
    }
}
