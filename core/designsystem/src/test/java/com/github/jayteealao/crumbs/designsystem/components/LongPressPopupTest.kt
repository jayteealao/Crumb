package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
                val bundle = defaultPopupActions()
                CrumbsLongPressPopup(
                    visible = true,
                    onDismiss = {},
                    actions = bundle.actions,
                    onSelect = bundle.onSelect,
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
                val bundle = defaultPopupActions()
                CrumbsLongPressPopup(
                    visible = true,
                    onDismiss = {},
                    actions = bundle.actions,
                    onSelect = bundle.onSelect,
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
        val firedActions = mutableListOf<String>()
        var dismissCount = 0

        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                val bundle = bookmarkPopupActions(
                    onTag = { firedActions += "tag" },
                    onOpen = { firedActions += "open" },
                    onShare = { firedActions += "share" },
                    onDelete = { firedActions += "delete" },
                )
                CrumbsLongPressPopup(
                    visible = true,
                    onDismiss = { dismissCount++ },
                    actions = bundle.actions,
                    onSelect = bundle.onSelect,
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

    // AC1 — the full-screen scrim must absorb taps anywhere off the action card,
    // including the region above/left of the fingertip anchor, and dismiss the
    // popup instead of leaking the tap to the list beneath.
    @Test
    fun popup_scrim_absorbs_tap_above_anchor() {
        var dismissed = false

        composeTestRule.setContent {
            var visible by remember { mutableStateOf(true) }
            CrumbsTheme(darkTheme = false) {
                val bundle = bookmarkPopupActions(
                    onTag = {},
                    onOpen = {},
                    onShare = {},
                    onDelete = {},
                )
                CrumbsLongPressPopup(
                    visible = visible,
                    onDismiss = {
                        dismissed = true
                        visible = false
                    },
                    actions = bundle.actions,
                    onSelect = bundle.onSelect,
                    // Anchor well away from the top-left corner so (10, 10) is
                    // guaranteed to land on the scrim, not the card.
                    anchorOffsetPx = Offset(300f, 400f),
                )
            }
        }

        composeTestRule.onNodeWithTag("popup-scrim")
            .performTouchInput { click(position = Offset(10f, 10f)) }
        composeTestRule.waitForIdle()

        assertTrue("Tap above/left of the anchor must dismiss via the scrim", dismissed)
        composeTestRule.onNodeWithTag("popup").assertDoesNotExist()
    }
}
