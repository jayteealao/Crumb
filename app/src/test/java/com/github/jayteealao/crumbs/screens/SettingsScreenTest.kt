package com.github.jayteealao.crumbs.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.dropbox.differ.SimpleImageComparator
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.twitter.data.dto.SyncStatus
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.firebase.Timestamp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val options = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(
            imageComparator = SimpleImageComparator(maxDistance = 0.01f),
        ),
    )

    private val linkedStatus = SyncStatus(
        linked = true,
        lastPolledAt = Timestamp(Date(1_716_400_000_000L)),
        lastError = null,
        itemsAdded = 12,
        xUserId = "x-uid",
        latestTweetId = "1810000000000000000",
    )

    private val erroredStatus = SyncStatus(
        linked = false,
        lastPolledAt = Timestamp(Date(1_716_400_000_000L)),
        lastError = "invalid_grant",
    )

    @Test
    fun settings_linked_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                SettingsScreen(syncStatus = linkedStatus, onDisconnectClick = {})
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/SettingsScreen_linked_light.png", options)
    }

    @Test
    fun settings_linked_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                SettingsScreen(syncStatus = linkedStatus, onDisconnectClick = {})
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/SettingsScreen_linked_dark.png", options)
    }

    @Test
    fun settings_errored_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                SettingsScreen(syncStatus = erroredStatus, onDisconnectClick = {})
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/SettingsScreen_errored_light.png", options)
    }

    @Test
    fun settings_disconnected_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                SettingsScreen(syncStatus = SyncStatus(linked = false), onDisconnectClick = {})
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/SettingsScreen_disconnected_dark.png", options)
    }
}
