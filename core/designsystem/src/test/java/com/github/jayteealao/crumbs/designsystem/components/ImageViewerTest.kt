package com.github.jayteealao.crumbs.designsystem.components

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.test.FakeImageLoaderEngine
import com.github.jayteealao.crumbs.designsystem.TestCrumbsTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Behavioural tests for [CrumbsImageViewer] — the full-screen, paged, zoomable image
 * viewer opened by tapping a bookmark card image (AC4).
 *
 * These cover what is observable without a device or a logged-in feed: the viewer
 * opens at the tapped index (page indicator reflects it), the close affordance
 * dismisses it, the page indicator hides for a single image, and a horizontal swipe
 * advances the page. Pinch / double-tap zoom and the live tap-from-the-feed path
 * remain device-fidelity gates (the feed list is server-auth gated), so they are
 * not asserted here.
 *
 * A synchronous [FakeImageLoaderEngine] resolves every Telephoto image request to a
 * solid drawable so the pager pages render deterministically off-device.
 */
@OptIn(ExperimentalCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ImageViewerTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val threeImages = listOf(
        "https://img/1.jpg",
        "https://img/2.jpg",
        "https://img/3.jpg",
    )

    @Before
    fun installFakeImageLoader() {
        val engine = FakeImageLoaderEngine.Builder()
            .default(ColorDrawable(Color.rgb(0x88, 0x88, 0x88)))
            .build()
        val imageLoader = ImageLoader.Builder(ApplicationProvider.getApplicationContext())
            .components { add(engine) }
            .build()
        Coil.setImageLoader(imageLoader)
    }

    @After
    fun resetImageLoader() {
        Coil.reset()
    }

    @Test
    fun opensAtTappedIndex_indicatorReflectsInitialPage() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                CrumbsImageViewer(imageUrls = threeImages, initialIndex = 1, onDismiss = {})
            }
        }
        // initialIndex 1 → page 2 of 3.
        composeTestRule.onNodeWithTag("image-viewer-screen").assertExists()
        composeTestRule.onNodeWithTag("image-viewer-indicator").assertTextEquals("2 / 3")
    }

    @Test
    fun closeAffordance_invokesOnDismiss() {
        var dismissed = false
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                CrumbsImageViewer(imageUrls = threeImages, initialIndex = 0, onDismiss = { dismissed = true })
            }
        }
        composeTestRule.onNodeWithTag("image-viewer-close").performClick()
        composeTestRule.runOnIdle { assertTrue(dismissed) }
    }

    @Test
    fun singleImage_hidesPageIndicator() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                CrumbsImageViewer(
                    imageUrls = listOf("https://img/only.jpg"),
                    initialIndex = 0,
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithTag("image-viewer-screen").assertExists()
        composeTestRule.onNodeWithTag("image-viewer-indicator").assertDoesNotExist()
    }

    @Test
    fun horizontalSwipe_advancesToNextPage() {
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                CrumbsImageViewer(imageUrls = threeImages, initialIndex = 0, onDismiss = {})
            }
        }
        composeTestRule.onNodeWithTag("image-viewer-indicator").assertTextEquals("1 / 3")
        composeTestRule.onNodeWithTag("image-viewer-screen").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("image-viewer-indicator").assertTextEquals("2 / 3")
    }
}
