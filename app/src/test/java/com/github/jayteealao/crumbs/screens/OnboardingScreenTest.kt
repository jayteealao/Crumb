package com.github.jayteealao.crumbs.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class OnboardingScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val options = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(
            imageComparator = SimpleImageComparator(maxDistance = 0.01f),
        ),
    )

    @Test
    fun onboardingScreen_page0_light() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                OnboardingScreen(
                    pagerState = rememberPagerState(pageCount = { BrutalistOnboardingPages.size }),
                    onCtaClick = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/OnboardingScreen_page0_light.png", options)
    }

    @Test
    fun onboardingScreen_page3_dark() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                OnboardingScreen(
                    pagerState = rememberPagerState(
                        pageCount = { BrutalistOnboardingPages.size },
                        initialPage = 3,
                    ),
                    onCtaClick = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/OnboardingScreen_page3_dark.png", options)
    }
}
