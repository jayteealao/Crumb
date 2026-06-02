package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage

/**
 * Full-screen, in-app, zoomable image viewer for the bookmark card images.
 *
 * Presented as a **raw [Dialog]** with `usePlatformDefaultWidth = false` — NOT an
 * `AlertDialog`, which truncates full-bleed content (Google issue #290502769) — so
 * it fills the screen over the current content. Pages horizontally through
 * [imageUrls] starting at [initialIndex]; each page supports pinch / double-tap
 * zoom + fling via Telephoto's [ZoomableAsyncImage] (sub-sampling avoids OOM on
 * large photos). System back and the brutalist CLOSE affordance both dismiss.
 *
 * Telephoto consumes all tap gestures, so the card tiles (plain `AsyncImage` +
 * `clickable`) are what open this viewer — never the other way around.
 */
@Composable
fun CrumbsImageViewer(
    imageUrls: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (imageUrls.isEmpty()) return

    val colors = LocalCrumbsColors.current
    val stroke = LocalCrumbsStroke.current
    val shapes = LocalCrumbsShapes.current
    val typography = LocalCrumbsTypography.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val pagerState = rememberPagerState(
            initialPage = initialIndex.coerceIn(0, imageUrls.lastIndex),
            pageCount = { imageUrls.size },
        )
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.ink)
                .testTag("image-viewer-screen"),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ZoomableAsyncImage(
                    model = imageUrls[page],
                    contentDescription = "Image ${page + 1} of ${imageUrls.size}",
                    modifier = Modifier.fillMaxSize(),
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
                    .testTag("image-viewer-close"),
            )

            // "n / N" page indicator, only meaningful for multi-image tweets.
            if (imageUrls.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${imageUrls.size}",
                    style = typography.captionMono,
                    color = colors.surface,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .testTag("image-viewer-indicator"),
                )
            }
        }
    }
}
