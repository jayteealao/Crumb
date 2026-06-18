package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
class TagChipTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tagChip_plain_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CrumbsTagChip(label = "kotlin", onClick = {})
                    CrumbsTagChip(label = "compose", onClick = {})
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTagChip_plain_light.png")
    }

    @Test
    fun tagChip_plain_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CrumbsTagChip(label = "kotlin", onClick = {})
                    CrumbsTagChip(label = "compose", onClick = {})
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTagChip_plain_dark.png")
    }

    @Test
    fun tagChip_filterActive_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                Row(modifier = Modifier.padding(16.dp)) {
                    CrumbsFilterChipActive(label = "article", onDismiss = {})
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTagChip_filterActive_light.png")
    }

    @Test
    fun tagChip_filterActive_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                Row(modifier = Modifier.padding(16.dp)) {
                    CrumbsFilterChipActive(label = "article", onDismiss = {})
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTagChip_filterActive_dark.png")
    }

    @Test
    fun tagChip_addTag_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                Row(modifier = Modifier.padding(16.dp)) {
                    CrumbsAddTagChip(onClick = {})
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsTagChip_addTag_light.png")
    }
}
