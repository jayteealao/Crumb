package com.github.jayteealao.crumbs.designsystem.components

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

/**
 * Full-screen, in-app video expand surface — the video sibling of [CrumbsImageViewer].
 *
 * Presented as a **raw [Dialog]** with `usePlatformDefaultWidth = false` so it fills the
 * screen over the current content. A full-bleed [ContentFrame] hosts the SHARED [player]
 * (the same single ExoPlayer the inline card uses); the host hands the player to exactly one
 * surface at a time — when this viewer is open the inline card is passed `null` so the two
 * surfaces never fight over the player. [CrumbsVideoControls] overlays the brutalist transport,
 * and both system back and the `[ CLOSE ]` affordance dismiss.
 *
 * `@UnstableApi` because [ContentFrame] is opt-in.
 */
@OptIn(UnstableApi::class)
@Composable
fun CrumbsVideoViewer(
    player: Player?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCrumbsColors.current
    val stroke = LocalCrumbsStroke.current
    val shapes = LocalCrumbsShapes.current
    val typography = LocalCrumbsTypography.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.ink)
                .testTag("video-viewer-screen"),
        ) {
            ContentFrame(
                player = player,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                shutter = { Box(Modifier.fillMaxSize().background(Color.Black)) },
            )

            if (player != null) {
                CrumbsVideoControls(
                    player = player,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            // Brutalist close affordance — bordered mono label, top-end.
            Text(
                text = "[ CLOSE ]",
                style = typography.captionMono,
                color = colors.ink,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(colors.surface)
                    .border(stroke.regular, colors.ink, shapes.card)
                    .clickable { onDismiss() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("video-viewer-close"),
            )
        }
    }
}
