package com.github.jayteealao.crumbs.designsystem.layouts

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
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
class HomeScaffoldTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun homeScaffold_default_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                HomeScaffold(
                    topBar = { StubBlock(label = "topBar", heightDp = 88) },
                    filterBar = { StubBlock(label = "filterBar", heightDp = 34) },
                    bottomBar = { StubBlock(label = "bottomBar", heightDp = 52) },
                ) { padding ->
                    Box(
                        Modifier
                            .padding(padding)
                            .fillMaxSize()
                            .background(LocalCrumbsColors.current.surface),
                    )
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/HomeScaffold_default_light.png")
    }

    @Test
    fun homeScaffold_default_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                HomeScaffold(
                    topBar = { StubBlock(label = "topBar", heightDp = 88) },
                    filterBar = { StubBlock(label = "filterBar", heightDp = 34) },
                    bottomBar = { StubBlock(label = "bottomBar", heightDp = 52) },
                ) { padding ->
                    Box(
                        Modifier
                            .padding(padding)
                            .fillMaxSize()
                            .background(LocalCrumbsColors.current.surface),
                    )
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/HomeScaffold_default_dark.png")
    }
}

@Composable
private fun StubBlock(label: String, heightDp: Int) {
    val colors = LocalCrumbsColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .background(colors.ink.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = colors.ink)
    }
}
