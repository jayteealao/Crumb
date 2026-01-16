package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Share
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
class MetricsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun metrics_horizontal_normal_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                EngagementMetrics(
                    metrics = listOf(
                        MetricValue(Icons.Default.Favorite, 234, "Likes", onClick = {}),
                        MetricValue(Icons.Default.Reply, 45, "Replies", onClick = {}),
                        MetricValue(Icons.Default.Repeat, 12, "Reposts", onClick = {}),
                        MetricValue(Icons.Default.Share, 8, "Shares", onClick = {})
                    ),
                    layout = MetricsLayout.Horizontal
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/EngagementMetrics_horizontal_normal_light.png")
    }

    @Test
    fun metrics_horizontal_normal_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                EngagementMetrics(
                    metrics = listOf(
                        MetricValue(Icons.Default.Favorite, 234, "Likes", onClick = {}),
                        MetricValue(Icons.Default.Reply, 45, "Replies", onClick = {}),
                        MetricValue(Icons.Default.Repeat, 12, "Reposts", onClick = {}),
                        MetricValue(Icons.Default.Share, 8, "Shares", onClick = {})
                    ),
                    layout = MetricsLayout.Horizontal
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/EngagementMetrics_horizontal_normal_dark.png")
    }

    @Test
    fun metrics_horizontal_dense_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                EngagementMetrics(
                    metrics = listOf(
                        MetricValue(Icons.Default.Favorite, 1500, "Likes"),
                        MetricValue(Icons.Default.Reply, 89, "Replies"),
                        MetricValue(Icons.Default.Repeat, 234, "Reposts")
                    ),
                    layout = MetricsLayout.Horizontal,
                    dense = true
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/EngagementMetrics_horizontal_dense_light.png")
    }

    @Test
    fun metrics_vertical_normal_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                EngagementMetrics(
                    metrics = listOf(
                        MetricValue(Icons.Default.Favorite, 234, "Likes", onClick = {}),
                        MetricValue(Icons.Default.Reply, 45, "Replies", onClick = {}),
                        MetricValue(Icons.Default.Repeat, 12, "Reposts", onClick = {})
                    ),
                    layout = MetricsLayout.Vertical
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/EngagementMetrics_vertical_normal_light.png")
    }

    @Test
    fun metrics_vertical_normal_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                EngagementMetrics(
                    metrics = listOf(
                        MetricValue(Icons.Default.Favorite, 234, "Likes", onClick = {}),
                        MetricValue(Icons.Default.Reply, 45, "Replies", onClick = {}),
                        MetricValue(Icons.Default.Repeat, 12, "Reposts", onClick = {})
                    ),
                    layout = MetricsLayout.Vertical
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/EngagementMetrics_vertical_normal_dark.png")
    }

    @Test
    fun metrics_vertical_dense_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                EngagementMetrics(
                    metrics = listOf(
                        MetricValue(Icons.Default.Favorite, 5600, "Likes"),
                        MetricValue(Icons.Default.Reply, 789, "Replies"),
                        MetricValue(Icons.Default.Repeat, 1200, "Reposts")
                    ),
                    layout = MetricsLayout.Vertical,
                    dense = true
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/EngagementMetrics_vertical_dense_light.png")
    }
}
