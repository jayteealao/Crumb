package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
@Config(sdk = [33])
class ScaffoldTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun scaffold_empty_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsScaffold {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Content")
                    }
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsScaffold_empty_light.png")
    }

    @Test
    fun scaffold_empty_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsScaffold {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Content")
                    }
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsScaffold_empty_dark.png")
    }

    @Test
    fun scaffold_withTopBar_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsScaffold(
                    topBar = {
                        val colors = LocalCrumbsColors.current
                        Surface(
                            color = colors.surface,
                            modifier = Modifier.height(56.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text("Top Bar")
                            }
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(it),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Content with Top Bar")
                    }
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsScaffold_withTopBar_light.png")
    }

    @Test
    fun scaffold_withTopBar_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsScaffold(
                    topBar = {
                        val colors = LocalCrumbsColors.current
                        Surface(
                            color = colors.surface,
                            modifier = Modifier.height(56.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text("Top Bar")
                            }
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(it),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Content with Top Bar")
                    }
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsScaffold_withTopBar_dark.png")
    }

    @Test
    fun scaffold_withBottomBar_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsScaffold(
                    bottomBar = {
                        val colors = LocalCrumbsColors.current
                        Surface(
                            color = colors.surface,
                            modifier = Modifier.height(56.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text("Bottom Bar")
                            }
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(it),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Content with Bottom Bar")
                    }
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsScaffold_withBottomBar_light.png")
    }

    @Test
    fun scaffold_withBottomBar_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsScaffold(
                    bottomBar = {
                        val colors = LocalCrumbsColors.current
                        Surface(
                            color = colors.surface,
                            modifier = Modifier.height(56.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text("Bottom Bar")
                            }
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(it),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Content with Bottom Bar")
                    }
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsScaffold_withBottomBar_dark.png")
    }

    @Test
    fun scaffold_withFab_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                val colors = LocalCrumbsColors.current
                CrumbsScaffold(
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = {},
                            containerColor = colors.accent
                        ) {
                            Icon(Icons.Default.Add, "Add")
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(it),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Content with FAB")
                    }
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsScaffold_withFab_light.png")
    }

    @Test
    fun scaffold_withTopAndBottom_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                val colors = LocalCrumbsColors.current
                CrumbsScaffold(
                    topBar = {
                        Surface(
                            color = colors.surface,
                            modifier = Modifier.height(56.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text("Top Bar")
                            }
                        }
                    },
                    bottomBar = {
                        Surface(
                            color = colors.surface,
                            modifier = Modifier.height(56.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text("Bottom Bar")
                            }
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(it),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Content")
                    }
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsScaffold_withTopAndBottom_light.png")
    }
}
