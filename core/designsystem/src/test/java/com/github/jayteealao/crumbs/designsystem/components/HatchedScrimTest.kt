package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
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
class HatchedScrimTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun hatchedScrim_default_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                Box(Modifier.size(200.dp, 300.dp)) {
                    HatchedScrim(modifier = Modifier.size(200.dp, 300.dp))
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/HatchedScrim_default_light.png")
    }

    @Test
    fun hatchedScrim_default_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                Box(Modifier.size(200.dp, 300.dp)) {
                    HatchedScrim(modifier = Modifier.size(200.dp, 300.dp))
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/HatchedScrim_default_dark.png")
    }
}
