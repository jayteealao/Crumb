package com.github.jayteealao.crumbs.designsystem.components

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing

/**
 * Player state for video playback
 */
enum class PlayerState {
    Loading,
    Playing,
    Paused,
    Error
}

/**
 * Video player component for embedded video playback
 *
 * Uses ExoPlayer for video playback with custom controls overlay.
 * Supports tap to play/pause and displays loading/thumbnail states.
 * Bottom-end cut corner matches the card aesthetic.
 *
 * @param videoUrl URL of the video to play
 * @param thumbnailUrl Optional thumbnail to show before video loads
 * @param aspectRatio Aspect ratio for the player (default 16:9)
 * @param modifier Modifier to be applied to the component
 */
@Composable
fun CrumbsVideoPlayer(
    videoUrl: String,
    thumbnailUrl: String? = null,
    aspectRatio: Float = 16f / 9f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current

    var playerState by remember { mutableStateOf(PlayerState.Loading) }

    // Create ExoPlayer instance
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()

            // Listen for player state changes
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    playerState = when (state) {
                        Player.STATE_BUFFERING -> PlayerState.Loading
                        Player.STATE_READY -> if (isPlaying) PlayerState.Playing else PlayerState.Paused
                        Player.STATE_ENDED -> PlayerState.Paused
                        else -> playerState
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    playerState = PlayerState.Error
                }
            })

            playWhenReady = false
        }
    }

    // Clean up player when composable leaves composition
    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(CrumbsShapes.videoPlayer) // bottom-end cut corner (8dp)
            .background(colors.surfaceVariant)
            .clickable {
                if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                } else {
                    exoPlayer.play()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Video player view
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // Use custom controls
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Thumbnail (shown when loading and thumbnail is available)
        if (playerState == PlayerState.Loading && thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Loading indicator
        if (playerState == PlayerState.Loading) {
            CircularProgressIndicator(
                color = colors.accent
            )
        }

        // Play/Pause button overlay (visible when paused)
        if (playerState == PlayerState.Paused || playerState == PlayerState.Error) {
            IconButton(
                onClick = {
                    if (playerState == PlayerState.Error) {
                        // Retry on error
                        exoPlayer.prepare()
                        exoPlayer.play()
                    } else {
                        exoPlayer.play()
                    }
                },
                modifier = Modifier
                    .background(colors.surface.copy(alpha = 0.8f), shape = androidx.compose.foundation.shape.CircleShape)
                    .padding(spacing.md)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = colors.accent
                )
            }
        }
    }
}

// Previews
@Preview(name = "Video Player - Paused", showBackground = true)
@Composable
private fun PreviewVideoPlayerPaused() {
    CrumbsTheme(darkTheme = false) {
        // Note: Preview shows placeholder since actual video can't play in preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(CrumbsShapes.videoPlayer)
                .background(LocalCrumbsColors.current.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = {},
                modifier = Modifier
                    .background(
                        LocalCrumbsColors.current.surface.copy(alpha = 0.8f),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
                    .padding(LocalCrumbsSpacing.current.md)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = LocalCrumbsColors.current.accent
                )
            }
        }
    }
}

@Preview(name = "Video Player - Dark", showBackground = true)
@Composable
private fun PreviewVideoPlayerDark() {
    CrumbsTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(CrumbsShapes.videoPlayer)
                .background(LocalCrumbsColors.current.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = {},
                modifier = Modifier
                    .background(
                        LocalCrumbsColors.current.surface.copy(alpha = 0.8f),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
                    .padding(LocalCrumbsSpacing.current.md)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = LocalCrumbsColors.current.accent
                )
            }
        }
    }
}
