package com.github.jayteealao.crumbs.designsystem.components

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberMuteButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

/**
 * Brutalist transport controls for the inline / full-screen video player, built from the
 * media3-ui-compose state holders ([rememberPlayPauseButtonState], [rememberMuteButtonState],
 * [rememberProgressStateWithTickInterval]) — NOT Material3 prebuilt controls and NOT a legacy
 * `PlayerView` overlay. Styled to the ink / surface / mono tokens: a bordered mono play/pause
 * glyph, a hairline progress rule that fills with `ink`, and a mute toggle.
 *
 * Driven entirely off the shared [Player]; safe to recompose as the player's state changes.
 * `@UnstableApi` because every media3-ui-compose entry point is opt-in.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun CrumbsVideoControls(
    player: Player,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCrumbsColors.current
    val stroke = LocalCrumbsStroke.current
    val typography = LocalCrumbsTypography.current

    val playPause = rememberPlayPauseButtonState(player)
    val mute = rememberMuteButtonState(player)
    val progress = rememberProgressStateWithTickInterval(player, tickIntervalMs = 250L)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("bookmark-card-video-controls"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Play / pause — mono label (no glyph font dependency) in a hit target.
        Text(
            text = if (playPause.showPlay) "PLAY" else "PAUSE",
            style = typography.captionMono,
            color = colors.ink,
            modifier = Modifier
                .clickable(enabled = playPause.isEnabled) { playPause.onClick() }
                .padding(horizontal = 4.dp)
                .testTag("video-play-pause"),
        )

        // Hairline progress rule — fills with ink as playback advances. A thin
        // brutalist bar rather than a Material slider; tap-to-seek is intentionally
        // out of scope for v1 (tap-to-play + expand are the AC affordances).
        val duration = progress.durationMs.coerceAtLeast(0L)
        val fraction = if (duration > 0L) {
            (progress.currentPositionMs.toFloat() / duration).coerceIn(0f, 1f)
        } else {
            0f
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(stroke.emphasis)
                .background(colors.onSurfaceVariant)
                .testTag("video-progress"),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(colors.ink),
            )
        }

        // Mute toggle — mono label reflecting the player's volume state.
        Text(
            text = if (mute.showMuted) "MUTE" else "VOL",
            style = typography.captionMono,
            color = colors.ink,
            modifier = Modifier
                .width(36.dp)
                .clickable(enabled = mute.isEnabled) { mute.onClick() }
                .testTag("video-mute"),
        )
    }
}
