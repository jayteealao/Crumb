package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography
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
class ImageComponentsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun gradientImage_bottomToTop_medium_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                GradientImage(
                    imageUrl = "https://via.placeholder.com/800x450",
                    gradientDirection = GradientDirection.BottomToTop,
                    gradientIntensity = GradientIntensity.Medium,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/GradientImage_bottomToTop_medium_light.png")
    }

    @Test
    fun gradientImage_bottomToTop_medium_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                GradientImage(
                    imageUrl = "https://via.placeholder.com/800x450",
                    gradientDirection = GradientDirection.BottomToTop,
                    gradientIntensity = GradientIntensity.Medium,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/GradientImage_bottomToTop_medium_dark.png")
    }

    @Test
    fun gradientImage_topToBottom_dark_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                GradientImage(
                    imageUrl = "https://via.placeholder.com/800x450",
                    gradientDirection = GradientDirection.TopToBottom,
                    gradientIntensity = GradientIntensity.Dark,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/GradientImage_topToBottom_dark_light.png")
    }

    @Test
    fun gradientImage_topToBottom_light_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                GradientImage(
                    imageUrl = "https://via.placeholder.com/800x450",
                    gradientDirection = GradientDirection.TopToBottom,
                    gradientIntensity = GradientIntensity.Light,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/GradientImage_topToBottom_light_light.png")
    }

    @Test
    fun gradientImage_leftToRight_medium_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                GradientImage(
                    imageUrl = "https://via.placeholder.com/800x450",
                    gradientDirection = GradientDirection.LeftToRight,
                    gradientIntensity = GradientIntensity.Medium,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/GradientImage_leftToRight_medium_light.png")
    }

    @Test
    fun gradientImage_rightToLeft_medium_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                GradientImage(
                    imageUrl = "https://via.placeholder.com/800x450",
                    gradientDirection = GradientDirection.RightToLeft,
                    gradientIntensity = GradientIntensity.Medium,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/GradientImage_rightToLeft_medium_light.png")
    }

    @Test
    fun gradientImage_diagonalTLBR_medium_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                GradientImage(
                    imageUrl = "https://via.placeholder.com/800x450",
                    gradientDirection = GradientDirection.DiagonalTLBR,
                    gradientIntensity = GradientIntensity.Medium,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/GradientImage_diagonalTLBR_medium_light.png")
    }

    @Test
    fun gradientImage_diagonalBLTR_medium_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                GradientImage(
                    imageUrl = "https://via.placeholder.com/800x450",
                    gradientDirection = GradientDirection.DiagonalBLTR,
                    gradientIntensity = GradientIntensity.Medium,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/GradientImage_diagonalBLTR_medium_light.png")
    }

    @Test
    fun gradientImage_withOverlay_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                val typography = LocalCrumbsTypography.current
                GradientImage(
                    imageUrl = "https://via.placeholder.com/800x450",
                    gradientDirection = GradientDirection.BottomToTop,
                    gradientIntensity = GradientIntensity.Dark,
                    modifier = Modifier.fillMaxSize(),
                    overlayContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            Text(
                                text = "Overlay Text",
                                style = typography.titleLarge,
                                color = Color.White
                            )
                        }
                    }
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/GradientImage_withOverlay_light.png")
    }

    @Test
    fun gradientImage_withOverlay_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                val typography = LocalCrumbsTypography.current
                GradientImage(
                    imageUrl = "https://via.placeholder.com/800x450",
                    gradientDirection = GradientDirection.BottomToTop,
                    gradientIntensity = GradientIntensity.Dark,
                    modifier = Modifier.fillMaxSize(),
                    overlayContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            Text(
                                text = "Overlay Text",
                                style = typography.titleLarge,
                                color = Color.White
                            )
                        }
                    }
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/GradientImage_withOverlay_dark.png")
    }
}
