package com.github.jayteealao.crumbs.designsystem.components

import androidx.activity.ComponentActivity
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
class MediaComponentsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val sampleMediaItems = listOf(
        MediaItem("1", MediaType.IMAGE, "https://via.placeholder.com/800x450", "Image 1"),
        MediaItem("2", MediaType.IMAGE, "https://via.placeholder.com/800x450", "Image 2"),
        MediaItem("3", MediaType.IMAGE, "https://via.placeholder.com/800x450", "Image 3")
    )

    @Test
    fun carousel_wide_dots_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                MediaCarousel(
                    items = sampleMediaItems,
                    indicatorStyle = CarouselIndicatorStyle.Dots,
                    aspectRatio = CarouselAspectRatio.Wide
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/MediaCarousel_wide_dots_light.png")
    }

    @Test
    fun carousel_wide_dots_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                MediaCarousel(
                    items = sampleMediaItems.take(2),
                    indicatorStyle = CarouselIndicatorStyle.Dots,
                    aspectRatio = CarouselAspectRatio.Wide
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/MediaCarousel_wide_dots_dark.png")
    }

    @Test
    fun carousel_wide_lines_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                MediaCarousel(
                    items = sampleMediaItems,
                    indicatorStyle = CarouselIndicatorStyle.Lines,
                    aspectRatio = CarouselAspectRatio.Wide
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/MediaCarousel_wide_lines_light.png")
    }

    @Test
    fun carousel_wide_none_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                MediaCarousel(
                    items = sampleMediaItems.take(1),
                    indicatorStyle = CarouselIndicatorStyle.None,
                    aspectRatio = CarouselAspectRatio.Wide
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/MediaCarousel_wide_none_light.png")
    }

    @Test
    fun carousel_square_dots_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                MediaCarousel(
                    items = listOf(
                        MediaItem("1", MediaType.IMAGE, "https://via.placeholder.com/600x600", "Image 1"),
                        MediaItem("2", MediaType.IMAGE, "https://via.placeholder.com/600x600", "Image 2")
                    ),
                    indicatorStyle = CarouselIndicatorStyle.Dots,
                    aspectRatio = CarouselAspectRatio.Square
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/MediaCarousel_square_dots_light.png")
    }

    @Test
    fun carousel_square_lines_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                MediaCarousel(
                    items = listOf(
                        MediaItem("1", MediaType.IMAGE, "https://via.placeholder.com/600x600", "Image 1"),
                        MediaItem("2", MediaType.IMAGE, "https://via.placeholder.com/600x600", "Image 2"),
                        MediaItem("3", MediaType.IMAGE, "https://via.placeholder.com/600x600", "Image 3")
                    ),
                    indicatorStyle = CarouselIndicatorStyle.Lines,
                    aspectRatio = CarouselAspectRatio.Square
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/MediaCarousel_square_lines_light.png")
    }

    @Test
    fun carousel_tall_dots_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                MediaCarousel(
                    items = listOf(
                        MediaItem("1", MediaType.IMAGE, "https://via.placeholder.com/450x800", "Image 1"),
                        MediaItem("2", MediaType.IMAGE, "https://via.placeholder.com/450x800", "Image 2")
                    ),
                    indicatorStyle = CarouselIndicatorStyle.Dots,
                    aspectRatio = CarouselAspectRatio.Tall
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/MediaCarousel_tall_dots_light.png")
    }

    @Test
    fun carousel_tall_none_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                MediaCarousel(
                    items = listOf(
                        MediaItem("1", MediaType.IMAGE, "https://via.placeholder.com/450x800", "Image 1")
                    ),
                    indicatorStyle = CarouselIndicatorStyle.None,
                    aspectRatio = CarouselAspectRatio.Tall
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/MediaCarousel_tall_none_light.png")
    }

    @Test
    fun carousel_square_dots_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                MediaCarousel(
                    items = listOf(
                        MediaItem("1", MediaType.IMAGE, "https://via.placeholder.com/600x600", "Image 1"),
                        MediaItem("2", MediaType.IMAGE, "https://via.placeholder.com/600x600", "Image 2")
                    ),
                    indicatorStyle = CarouselIndicatorStyle.Dots,
                    aspectRatio = CarouselAspectRatio.Square
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/MediaCarousel_square_dots_dark.png")
    }

    @Test
    fun carousel_tall_dots_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                MediaCarousel(
                    items = listOf(
                        MediaItem("1", MediaType.IMAGE, "https://via.placeholder.com/450x800", "Image 1"),
                        MediaItem("2", MediaType.IMAGE, "https://via.placeholder.com/450x800", "Image 2")
                    ),
                    indicatorStyle = CarouselIndicatorStyle.Dots,
                    aspectRatio = CarouselAspectRatio.Tall
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/MediaCarousel_tall_dots_dark.png")
    }
}
