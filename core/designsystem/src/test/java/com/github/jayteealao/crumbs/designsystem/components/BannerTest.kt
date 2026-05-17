package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BannerTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun banner_syncError_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsBanner(
                    kickerLine = "↳ Sync error",
                    detail = "Twitter session expired",
                    ctaLabel = "Reconnect",
                    onCta = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsBanner_syncError_light.png")
    }

    @Test
    fun banner_syncError_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsBanner(
                    kickerLine = "↳ Sync error",
                    detail = "Twitter session expired",
                    ctaLabel = "Reconnect",
                    onCta = {},
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsBanner_syncError_dark.png")
    }

    @Test
    fun banner_success_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsBanner(
                    kickerLine = "↳ Synced",
                    detail = "212 bookmarks updated",
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsBanner_success_light.png")
    }

    @Test
    fun banner_success_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsBanner(
                    kickerLine = "↳ Synced",
                    detail = "212 bookmarks updated",
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsBanner_success_dark.png")
    }
}
