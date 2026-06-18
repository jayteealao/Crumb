package com.github.jayteealao.crumbs.designsystem.components

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.TestExoPlayerBuilder
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
 * Behavioural tests for [CrumbsVideoPlayer], the inline video band.
 *
 * Covers the observable contract off-device: the inactive state shows the poster + brutalist
 * play badge (tap → onPlayClick), the expand affordance fires onExpand, and the active state
 * (a [TestExoPlayerBuilder] player attached) swaps the play badge for the transport controls
 * and the player surface. Real HLS/DASH decode, the on-device 50+-card memory profile, and
 * pinch/scroll recycle remain device-fidelity gates (recorded as a runtime-evidence deferral).
 *
 * A synchronous [FakeImageLoaderEngine] resolves the Coil poster request to a solid drawable.
 */
@OptIn(ExperimentalCoilApi::class, UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CrumbsVideoPlayerTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var player: ExoPlayer? = null

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
    fun tearDown() {
        player?.release()
        Coil.reset()
    }

    @Test
    fun inactiveState_showsPlayBadge_andTapInvokesOnPlay() {
        var played = false
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                CrumbsVideoPlayer(
                    posterUrl = "https://img/poster.jpg",
                    player = null,
                    onPlayClick = { played = true },
                    onExpand = {},
                )
            }
        }
        composeTestRule.onNodeWithTag("bookmark-card-video-play").assertExists()
        composeTestRule.onNodeWithTag("bookmark-card-video-controls").assertDoesNotExist()

        composeTestRule.onNodeWithTag("bookmark-card-video-play").performClick()
        composeTestRule.runOnIdle { assertTrue(played) }
    }

    @Test
    fun expandAffordance_invokesOnExpand() {
        var expanded = false
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                CrumbsVideoPlayer(
                    posterUrl = "https://img/poster.jpg",
                    player = null,
                    onPlayClick = {},
                    onExpand = { expanded = true },
                )
            }
        }
        composeTestRule.onNodeWithTag("bookmark-card-video-expand").performClick()
        composeTestRule.runOnIdle { assertTrue(expanded) }
    }

    @Test
    fun activeState_showsControls_andSurface_notPlayBadge() {
        val p = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext()).build()
        player = p
        composeTestRule.setContent {
            TestCrumbsTheme(darkTheme = false) {
                CrumbsVideoPlayer(
                    posterUrl = "https://img/poster.jpg",
                    player = p,
                    onPlayClick = {},
                    onExpand = {},
                )
            }
        }
        composeTestRule.onNodeWithTag("bookmark-card-video").assertExists()
        composeTestRule.onNodeWithTag("bookmark-card-video-controls").assertExists()
        composeTestRule.onNodeWithTag("bookmark-card-video-play").assertDoesNotExist()
    }
}
