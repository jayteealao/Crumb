package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

// Brutalist media grid for the bookmark card. The single-image case stays in the
// card's 16:7 band (see BookmarkCardMedia); 2+ images render here as a fixed,
// eager Column/Row grid — never a nested LazyVerticalGrid, which crashes inside a
// LazyColumn item with infinite-height constraints. The first four photos show;
// a fifth+ collapses into a "+N" ink scrim on the fourth tile (Twitter/X style).
// Hairline gaps reveal the parent ink background, matching the card border weight.

private const val MAX_GRID_TILES = 4

/**
 * Single Coil image with the brutalist accent (#FF5A1F) loading placeholder.
 *
 * Two Coil-2 gotchas are encoded here:
 *  - #1908: the `placeholder=` painter only applies on a painter *state transition*,
 *    so we drive an accent [Box] background behind the image and clear it from the
 *    `onSuccess`/`onError` callbacks instead.
 *  - #1940: rebuilding the [ImageRequest] on every recomposition makes a LazyColumn
 *    scroll-state read reload every visible image, so the request is `remember(url)`-ed.
 */
@Composable
internal fun CrumbsCardMediaImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCrumbsColors.current
    val context = LocalContext.current
    // No per-request crossfade: the app-level ImageLoader (CrumbApplication) already
    // applies a global crossfade, and a request-level fade animates from alpha 0 —
    // which makes the synchronous Roborazzi goldens capture a mid-fade transparent tile.
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .build()
    }
    var settled by remember(url) { mutableStateOf(false) }
    Box(
        modifier = modifier.background(if (settled) Color.Transparent else colors.accent),
    ) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onSuccess = { settled = true },
            onError = { settled = true },
        )
    }
}

/**
 * 2×2 brutalist photo grid. [images] is the full ordered photo list; only the first
 * four tiles render, with any remainder surfaced as a "+N" overlay on the last tile.
 * [onImageClick] is invoked with the *absolute* index in [images] so the caller can
 * open the full-screen viewer at the tapped page (the "+N" tile opens at index 3).
 */
@Composable
internal fun BookmarkCardImageGrid(
    images: List<String>,
    onImageClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCrumbsColors.current
    val stroke = LocalCrumbsStroke.current
    val shown = images.take(MAX_GRID_TILES)
    val overflow = images.size - shown.size

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.ink),
        verticalArrangement = Arrangement.spacedBy(stroke.hairline),
    ) {
        when (shown.size) {
            // 2 → one row of two squares.
            2 -> GridRow {
                GridTile(shown[0], index = 0, overflowCount = 0, onImageClick)
                GridTile(shown[1], index = 1, overflowCount = 0, onImageClick)
            }
            // 3 → two on top, one wide below.
            3 -> {
                GridRow {
                    GridTile(shown[0], index = 0, overflowCount = 0, onImageClick)
                    GridTile(shown[1], index = 1, overflowCount = 0, onImageClick)
                }
                GridRow {
                    GridTile(shown[2], index = 2, overflowCount = 0, onImageClick)
                }
            }
            // 4 (and 4-with-overflow) → full 2×2; the 4th tile carries the "+N" scrim.
            else -> {
                GridRow {
                    GridTile(shown[0], index = 0, overflowCount = 0, onImageClick)
                    GridTile(shown[1], index = 1, overflowCount = 0, onImageClick)
                }
                GridRow {
                    GridTile(shown[2], index = 2, overflowCount = 0, onImageClick)
                    GridTile(shown[3], index = 3, overflowCount = overflow, onImageClick)
                }
            }
        }
    }
}

/** A full-width grid row laid out at 2:1 so its tiles read as squares (two-up). */
@Composable
private fun GridRow(content: @Composable RowScope.() -> Unit) {
    val stroke = LocalCrumbsStroke.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f),
        horizontalArrangement = Arrangement.spacedBy(stroke.hairline),
        content = content,
    )
}

/**
 * One grid cell. [overflowCount] > 0 paints the translucent ink scrim + "+N" label
 * (only the last visible tile uses it). Tapping any tile reports its absolute index.
 */
@Composable
private fun RowScope.GridTile(
    url: String,
    index: Int,
    overflowCount: Int,
    onImageClick: (index: Int) -> Unit,
) {
    val colors = LocalCrumbsColors.current
    val typography = LocalCrumbsTypography.current
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .testTag("bookmark-card-image")
            .clickable { onImageClick(index) },
    ) {
        CrumbsCardMediaImage(
            url = url,
            contentDescription = "Image ${index + 1}",
            modifier = Modifier.fillMaxSize(),
        )
        if (overflowCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.ink.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$overflowCount",
                    style = typography.captionMono,
                    color = colors.surface,
                    modifier = Modifier.testTag("bookmark-card-image-overflow"),
                )
            }
        }
    }
}
