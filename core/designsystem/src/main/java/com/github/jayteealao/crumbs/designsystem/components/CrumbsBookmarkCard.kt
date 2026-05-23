package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.github.jayteealao.crumbs.designsystem.modifiers.brutalistStrikethrough
import com.github.jayteealao.crumbs.designsystem.modifiers.dashedDivider
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography
import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.models.ContentType
import com.github.jayteealao.crumbs.models.toRelativeTime

// Brutalist CrumbsBookmarkCard — composition follows handoff-components.jsx
// :395-405: media (aspect 16:7) → hairline → CrumbsIndexStrip header →
// padding(14.dp) Column [ title (UPPERCASE, displayHeadline) → spacer 8 →
// preview (bodyMono, maxLines=3) → dashed footer divider → engagement meta
// row → tag FlowRow using CrumbsTagChip ].

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CrumbsBookmarkCard(
    bookmark: Bookmark,
    onCardClick: (String) -> Unit,
    onLongPress: (Bookmark, Offset) -> Unit = { _, _ -> },
    index: Int = 0,
    isExpanded: Boolean = false,
    onConfirmDeletePending: ((String) -> Unit)? = null,
    onCancelDeletePending: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (bookmark.pendingDelete) {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        onCancelDeletePending?.invoke(bookmark.id)
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        onConfirmDeletePending?.invoke(bookmark.id)
                    }
                    SwipeToDismissBoxValue.Settled -> Unit
                }
                false
            },
        )
        SwipeToDismissBox(
            state = dismissState,
            modifier = modifier.testTag("bookmark-card-pending-${bookmark.id}"),
            backgroundContent = {},
        ) {
            BookmarkCardContent(
                bookmark = bookmark,
                onCardClick = onCardClick,
                onLongPress = onLongPress,
                index = index,
            )
        }
    } else {
        BookmarkCardContent(
            bookmark = bookmark,
            onCardClick = onCardClick,
            onLongPress = onLongPress,
            index = index,
            modifier = modifier,
        )
    }
}

@Composable
private fun BookmarkCardContent(
    bookmark: Bookmark,
    onCardClick: (String) -> Unit,
    onLongPress: (Bookmark, Offset) -> Unit,
    index: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val stroke = LocalCrumbsStroke.current
    val shapes = LocalCrumbsShapes.current
    val typography = LocalCrumbsTypography.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .border(stroke.regular, colors.ink, shapes.card)
            .testTag("bookmark-card")
            .pointerInput(bookmark.id) {
                detectTapGestures(
                    onTap = { onCardClick(bookmark.sourceUrl) },
                    onLongPress = { offsetPx -> onLongPress(bookmark, offsetPx) },
                )
            }
            .semantics {
                onClick(label = "Open bookmark") { onCardClick(bookmark.sourceUrl); true }
                onLongClick(label = "Show actions") { onLongPress(bookmark, Offset.Zero); true }
                if (bookmark.pendingDelete) {
                    stateDescription = "Pending removal — swipe to confirm or cancel"
                    liveRegion = LiveRegionMode.Polite
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Optional media at top (16:7 aspect per handoff-components.jsx:367).
            val mediaUrl = bookmark.imageUrl
            if (mediaUrl != null &&
                (bookmark.contentType == ContentType.Image || bookmark.contentType == ContentType.Video)
            ) {
                AsyncImage(
                    model = mediaUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 7f),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(stroke.hairline)
                        .background(colors.ink),
                )
            }

            // CrumbsIndexStrip header replaces the inline source/author/age row.
            CrumbsIndexStrip(
                index = "%03d".format(index),
                source = bookmark.source,
                author = bookmark.author,
                trailing = bookmark.savedAt.toRelativeTime(),
            )
            // 1.5dp hairline separator below the strip.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(stroke.hairline)
                    .background(colors.ink),
            )

            // Body column — title, preview, dashed footer, meta, tags.
            Column(
                modifier = Modifier.padding(spacing.md + 2.dp), // 14dp
            ) {
                Text(
                    text = bookmark.title.uppercase(),
                    style = typography.displayHeadline,
                    color = colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .testTag(if (bookmark.pendingDelete) "bookmark-card-strikethrough" else "card-title")
                        .brutalistStrikethrough(active = bookmark.pendingDelete, color = colors.ink),
                )
                Spacer(Modifier.height(spacing.sm))
                Text(
                    text = bookmark.previewText,
                    style = typography.bodyMono,
                    color = colors.ink,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (bookmark.isThread) {
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = "↳ + ${bookmark.threadCount} MORE",
                        style = typography.captionMono,
                        color = colors.ink,
                    )
                }
                Spacer(Modifier.height(spacing.sm))
                // Dashed 1dp footer divider — per handoff-components.jsx:114, 370.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .dashedDivider(
                            color = colors.ink,
                            strokeWidth = stroke.hairline,
                            dashLengthDp = 4.dp,
                            gapDp = 3.dp,
                        ),
                )
                Spacer(Modifier.height(spacing.xs))
                // Engagement meta row — "IMAGE · ↑ 2.4k" / "TEXT" (when null).
                val typeLabel = bookmark.contentType.name.uppercase()
                val meta = bookmark.engagementCount?.let { "$typeLabel · ↑ ${formatCount(it)}" } ?: typeLabel
                Text(
                    text = meta,
                    style = typography.metaMono,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.testTag("card-meta"),
                )
                if (bookmark.tags.isNotEmpty()) {
                    Spacer(Modifier.height(spacing.sm))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.md),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                        modifier = Modifier.testTag("card-actions"),
                    ) {
                        bookmark.tags.forEach { tag ->
                            CrumbsTagChip(label = tag, onClick = { /* future: filter by tag */ })
                        }
                    }
                }
            }
        }

        if (bookmark.isDeleted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.surface.copy(alpha = 0.97f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "CONTENT UNAVAILABLE",
                    style = typography.captionMono,
                    color = colors.ink,
                )
            }
        }
    }
}

// k/m suffix formatter matching the JS demo at handoff-components.jsx:117.
private fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1fm".format(n / 1_000_000.0).removeSuffix(".0m") + if (n % 1_000_000 == 0) "m" else ""
    n >= 10_000 -> "${n / 1000}k"
    n >= 1_000 -> "%.1fk".format(n / 1000.0)
    else -> n.toString()
}

// Sample data for previews
private val sampleTwitterText = Bookmark(
    id = "1",
    source = BookmarkSource.Twitter,
    author = "@designpatterns",
    title = "Understanding SOLID Principles",
    previewText = "Let me explain the five SOLID principles that every developer should know. These fundamental concepts will help you write better, more maintainable code.",
    contentType = ContentType.Text,
    savedAt = System.currentTimeMillis() - 3600000,
    tags = listOf("programming", "design"),
    sourceUrl = "https://twitter.com/i/web/status/123",
    engagementCount = 247,
)

private val sampleTwitterImage = Bookmark(
    id = "2",
    source = BookmarkSource.Twitter,
    author = "@kotlinconf",
    title = "Compose Multiplatform is here!",
    previewText = "Excited to announce the stable release of Compose Multiplatform. Build beautiful UIs for Android, iOS, Desktop, and Web.",
    imageUrl = "https://example.com/image.jpg",
    contentType = ContentType.Image,
    savedAt = System.currentTimeMillis() - 7200000,
    tags = listOf("kotlin", "compose", "multiplatform"),
    sourceUrl = "https://twitter.com/i/web/status/124",
    engagementCount = 2400,
)

private val sampleTwitterThread = Bookmark(
    id = "3",
    source = BookmarkSource.Twitter,
    author = "@architectpatterns",
    title = "Clean Architecture Thread",
    previewText = "1/ Let's talk about Clean Architecture and why it matters for modern Android development...",
    contentType = ContentType.Thread,
    savedAt = System.currentTimeMillis() - 86400000,
    tags = listOf("architecture", "android"),
    isThread = true,
    threadCount = 12,
    sourceUrl = "https://twitter.com/i/web/status/125",
)

private val sampleRedditPost = Bookmark(
    id = "4",
    source = BookmarkSource.Reddit,
    author = "u/androiddev",
    title = "Tips for optimizing RecyclerView performance",
    previewText = "Here are some lesser-known tips for getting better performance out of RecyclerView. These helped me reduce jank significantly in my production app.",
    contentType = ContentType.Text,
    savedAt = System.currentTimeMillis() - 172800000,
    tags = listOf("android", "performance"),
    sourceUrl = "https://reddit.com/r/androiddev/comments/abc123",
)

private val sampleDeletedBookmark = Bookmark(
    id = "5",
    source = BookmarkSource.Twitter,
    author = "@deleteduser",
    title = "This tweet has been deleted",
    previewText = "This content is no longer available.",
    contentType = ContentType.Text,
    savedAt = System.currentTimeMillis() - 604800000,
    isDeleted = true,
    sourceUrl = "https://twitter.com/i/web/status/126",
)

@Preview(name = "Twitter Text Light", showBackground = true)
@Composable
private fun PreviewTwitterTextLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsBookmarkCard(bookmark = sampleTwitterText, onCardClick = {}, index = 1)
    }
}

@Preview(name = "Twitter Text Dark", showBackground = true)
@Composable
private fun PreviewTwitterTextDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsBookmarkCard(bookmark = sampleTwitterText, onCardClick = {}, index = 1)
    }
}

@Preview(name = "Twitter Image Light", showBackground = true)
@Composable
private fun PreviewTwitterImageLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsBookmarkCard(bookmark = sampleTwitterImage, onCardClick = {}, index = 2)
    }
}

@Preview(name = "Twitter Thread Light", showBackground = true)
@Composable
private fun PreviewTwitterThreadLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsBookmarkCard(bookmark = sampleTwitterThread, onCardClick = {}, index = 3)
    }
}

@Preview(name = "Reddit Post Light", showBackground = true)
@Composable
private fun PreviewRedditPostLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsBookmarkCard(bookmark = sampleRedditPost, onCardClick = {}, index = 4)
    }
}

@Preview(name = "Deleted Content Light", showBackground = true)
@Composable
private fun PreviewDeletedBookmarkLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsBookmarkCard(bookmark = sampleDeletedBookmark, onCardClick = {}, index = 5)
    }
}
