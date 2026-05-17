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
class LongPressPopupTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun popup_default_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsLongPressPopup(
                    visible = true,
                    onDismiss = {},
                    actions = defaultPopupActions(),
                    headerKicker = "Twitter",
                    headerHandle = "@designpatterns",
                    headerAge = "1h",
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsLongPressPopup_default_light.png")
    }

    @Test
    fun popup_default_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsLongPressPopup(
                    visible = true,
                    onDismiss = {},
                    actions = defaultPopupActions(),
                    headerKicker = "Reddit",
                    headerHandle = "u/androiddev",
                    headerAge = "2d",
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsLongPressPopup_default_dark.png")
    }
}
