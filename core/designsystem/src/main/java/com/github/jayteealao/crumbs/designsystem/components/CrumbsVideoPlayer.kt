package com.github.jayteealao.crumbs.designsystem.components

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

/**
 * Inline video surface for the bookmark card's 16:7 media band.
 *
 * Two states, driven solely by whether [player] is non-null:
 *  - **Inactive** ([player] == null): the Coil poster ([posterUrl]) under a brutalist
 *    `[ PLAY ]` badge. Tapping the badge calls [onPlayClick], which asks the host to make
 *    THIS card the single active video (the shared player attaches and [player] becomes
 *    non-null on the next recomposition). No `PlayerSurface` exists in this state, so a feed
 *    of many video cards holds at most one surface.
 *  - **Active** ([player] != null): [ContentFrame] renders the surface and covers it with
 *    the poster shutter until the first frame draws, with [CrumbsVideoControls] overlaid.
 *
 * The surface is tied to ACTIVE-ness, not play/pause: while [player] stays non-null, pausing
 * never removes `PlayerSurface` (avoiding the surface-lifecycle / first-frame-flash bug). The
 * host detaches by passing `null` (after `clearVideoSurface()` on the player) when the card
 * scrolls away or the full-screen viewer takes the surface.
 *
 * An expand affordance is always present so the user can open the full-screen viewer.
 * `@UnstableApi` because [ContentFrame] is opt-in.
 */
@OptIn(UnstableApi::class)
@Composable
fun CrumbsVideoPlayer(
    posterUrl: String?,
    player: Player?,
    onPlayClick: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCrumbsColors.current
    val stroke = LocalCrumbsStroke.current
    val shapes = LocalCrumbsShapes.current
    val typography = LocalCrumbsTypography.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 7f)
            .background(colors.ink)
            .testTag("bookmark-card-video"),
        contentAlignment = Alignment.Center,
    ) {
        if (player != null) {
            ContentFrame(
                player = player,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                shutter = { VideoPoster(posterUrl) },
            )
            CrumbsVideoControls(
                player = player,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else {
            VideoPoster(posterUrl, modifier = Modifier.matchParentSize())
            Text(
                text = "[ PLAY ]",
                style = typography.captionMono,
                color = colors.ink,
                modifier = Modifier
                    .background(colors.surface)
                    .border(stroke.regular, colors.ink, shapes.card)
                    .clickable { onPlayClick() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("bookmark-card-video-play"),
            )
        }

        // Expand-to-fullscreen affordance — bordered mono label, top-end, in both states.
        Text(
            text = "[ FULL ]",
            style = typography.captionMono,
            color = colors.ink,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(colors.surface)
                .border(stroke.regular, colors.ink, shapes.card)
                .clickable { onExpand() }
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("bookmark-card-video-expand"),
        )
    }
}

/**
 * Poster / shutter for the video band: the Coil thumbnail (reusing [CrumbsCardMediaImage]'s
 * accent placeholder treatment) over an ink fill, or just the ink fill when no poster exists.
 */
@Composable
private fun VideoPoster(posterUrl: String?, modifier: Modifier = Modifier) {
    val colors = LocalCrumbsColors.current
    Box(modifier = modifier.fillMaxSize().background(colors.ink)) {
        if (!posterUrl.isNullOrBlank()) {
            CrumbsCardMediaImage(
                url = posterUrl,
                contentDescription = "Video thumbnail",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
