package com.github.jayteealao.crumbs.designsystem.layouts

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class OnboardingShellTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun onboardingShell_page0_light() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                OnboardingShell(
                    pages = persistentListOf(
                        { StubPage(label = "Page 0") },
                        { StubPage(label = "Page 1") },
                        { StubPage(label = "Page 2") },
                    ),
                    footerCtaText = "NEXT",
                    onFooterCtaClick = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/OnboardingShell_page0_light.png")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun onboardingShell_withSignInLink_light() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                OnboardingShell(
                    pages = persistentListOf(
                        { StubPage(label = "Page 0") },
                        { StubPage(label = "Page 1") },
                        { StubPage(label = "Page 2") },
                    ),
                    signInLink = {
                        androidx.compose.material3.Text(
                            text = "or sign in →",
                            color = LocalCrumbsColors.current.ink,
                        )
                    },
                    footerCtaText = "NEXT",
                    onFooterCtaClick = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/OnboardingShell_withSignInLink_light.png")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun onboardingShell_page1_dark() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                OnboardingShell(
                    pages = persistentListOf(
                        { StubPage(label = "Page 0") },
                        { StubPage(label = "Page 1") },
                        { StubPage(label = "Page 2") },
                    ),
                    pagerState = rememberPagerState(pageCount = { 3 }, initialPage = 1),
                    footerCtaText = "NEXT",
                    onFooterCtaClick = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/OnboardingShell_page1_dark.png")
    }
}

@Composable
private fun StubPage(label: String) {
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
