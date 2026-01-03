package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
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
@Config(sdk = [33])
class CrumbsCardTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun card_normal_withContent_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsCard(
                    modifier = Modifier.fillMaxWidth(),
                    size = CardSize.Normal
                ) {
                    Text("Card Title")
                    Text("Card content")
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsCard_normal_withContent_light.png")
    }

    @Test
    fun card_normal_withContent_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsCard(
                    modifier = Modifier.fillMaxWidth(),
                    size = CardSize.Normal
                ) {
                    Text("Card Title")
                    Text("Card content")
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsCard_normal_withContent_dark.png")
    }

    @Test
    fun card_small_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsCard(
                    size = CardSize.Small
                ) {
                    Text("Small")
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsCard_small_light.png")
    }
}
