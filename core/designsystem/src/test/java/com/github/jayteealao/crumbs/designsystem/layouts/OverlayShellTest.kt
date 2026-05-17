package com.github.jayteealao.crumbs.designsystem.layouts

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class OverlayShellTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun overlayShell_open_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                OverlayShell(
                    visible = true,
                    onDismiss = {},
                    header = {
                        Text(
                            text = "Filters",
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    },
                    footer = {
                        Text(
                            text = "APPLY",
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    },
                ) {
                    Text(
                        text = "Overlay body",
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/OverlayShell_open_light.png")
    }

    @Test
    fun overlayShell_open_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                OverlayShell(
                    visible = true,
                    onDismiss = {},
                    header = {
                        Text(
                            text = "Filters",
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    },
                    footer = {
                        Text(
                            text = "APPLY",
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    },
                ) {
                    Text(
                        text = "Overlay body",
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/OverlayShell_open_dark.png")
    }

    @Test
    fun backdrop_tap_invokes_onDismiss() {
        var dismissed = false
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                OverlayShell(
                    visible = true,
                    onDismiss = { dismissed = true },
                ) {
                    Text("body")
                }
            }
        }
        composeTestRule.onNodeWithTag("overlay-shell-backdrop").performClick()
        assertTrue(dismissed)
    }
}
