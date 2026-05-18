package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
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

    @Test
    fun popup_action_clicks_invoke_callbacks_and_dismiss() {
        // The popup is the only escape from a long-press in the brutalist
        // redesign. Screenshot tests prove its appearance but not that the
        // taps actually wire through to per-action lambdas + the dismiss
        // callback. This test pins both contracts: each cell's onClick must
        // fire its own callback once and trigger dismissal.
        val firedActions = mutableListOf<String>()
        var dismissCount = 0

        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsLongPressPopup(
                    visible = true,
                    onDismiss = { dismissCount++ },
                    actions = bookmarkPopupActions(
                        onTag = { firedActions += "tag" },
                        onOpen = { firedActions += "open" },
                        onShare = { firedActions += "share" },
                        onDelete = { firedActions += "delete" },
                    ),
                )
            }
        }

        composeTestRule.onNodeWithTag("popup-action-tag").performClick()
        composeTestRule.onNodeWithTag("popup-action-open").performClick()
        composeTestRule.onNodeWithTag("popup-action-share").performClick()
        composeTestRule.onNodeWithTag("popup-action-delete").performClick()

        assertEquals(
            "Each tap must fire its own callback in order",
            listOf("tag", "open", "share", "delete"),
            firedActions,
        )
        assertTrue(
            "Every action click must also dismiss the popup (got $dismissCount dismisses)",
            dismissCount == 4,
        )
    }
}
