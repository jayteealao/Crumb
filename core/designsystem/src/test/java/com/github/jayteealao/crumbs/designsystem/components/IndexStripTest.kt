package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.models.BookmarkSource
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
class IndexStripTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun indexStrip_default_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsIndexStrip(
                    index = "247",
                    source = BookmarkSource.Twitter,
                    author = "@architectpatterns",
                    trailing = "5h ago",
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsIndexStrip_default_light.png")
    }

    @Test
    fun indexStrip_default_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsIndexStrip(
                    index = "247",
                    source = BookmarkSource.Twitter,
                    author = "@architectpatterns",
                    trailing = "5h ago",
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsIndexStrip_default_dark.png")
    }

    @Test
    fun indexStrip_inverted_searchHit_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsIndexStrip(
                    index = "HIT/01",
                    source = BookmarkSource.Reddit,
                    author = "u/androiddev",
                    trailing = "4 hits · 12ms",
                    inverted = true,
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsIndexStrip_inverted_searchHit_light.png")
    }
}
