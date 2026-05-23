package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
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

// Brutalist CrumbsBookmarkCard — Material3 Surface wrapper stripped. Sharp
// 1.5dp ink border on paper-surface background. detectTapGestures replaces
// combinedClickable so long-press surfaces the fingertip Offset (consumed by
// CrumbsLongPressPopup in the behaviors slice). onLongPress signature widened
// to `(Bookmark, Offset) -> Unit`.
//
// Caller migration: pass `Offset.Zero` if real popup wiring is deferred.

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CrumbsBookmarkCard(
    bookmark: Bookmark,
    onCardClick: (String) -> Unit,
    onLongPress: (Bookmark, Offset) -> Unit = { _, _ -> },
    isExpanded: Boolean = false,
    onConfirmDeletePending: ((String) -> Unit)? = null,
    onCancelDeletePending: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (bookmark.pendingDelete) {
        // `confirmValueChange` returns `false` so the box snaps back; actual row
        // removal is driven by the paging Flow once Room updates land.
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
            BookmarkCardContent(bookmark = bookmark, onCardClick = onCardClick, onLongPress = onLongPress)
        }
    } else {
        BookmarkCardContent(
            bookmark = bookmark,
            onCardClick = onCardClick,
            onLongPress = onLongPress,
            modifier = modifier,
        )
    }
}

@Composable
private fun BookmarkCardContent(
    bookmark: Bookmark,
    onCardClick: (String) -> Unit,
    onLongPress: (Bookmark, Offset) -> Unit,
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
                    // `stateDescription` layers after the visible title for
                    // TalkBack instead of replacing it, and the polite live
                    // region auto-announces when the row enters this state.
                    stateDescription = "Pending removal — swipe to confirm or cancel"
                    liveRegion = LiveRegionMode.Polite
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Full-width media (image or video thumbnail) at top
            val mediaUrl = bookmark.imageUrl
            if (mediaUrl != null &&
                (bookmark.contentType == ContentType.Image || bookmark.contentType == ContentType.Video)
            ) {
                AsyncImage(
                    model = mediaUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(stroke.hairline)
                        .background(colors.ink),
                )
            }

            // Header row: source icon + author kicker + separator + age
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(
                        id = when (bookmark.source) {
                            BookmarkSource.Twitter -> com.github.jayteealao.crumbs.designsystem.R.drawable.fatwitter
                            BookmarkSource.Reddit -> com.github.jayteealao.crumbs.designsystem.R.drawable.fareddit
                        },
                    ),
                    contentDescription = bookmark.source.displayName(),
                    modifier = Modifier.size(16.dp),
                    tint = colors.ink,
                )
                Text(
                    text = bookmark.author.uppercase(),
                    style = typography.captionMono,
                    color = colors.ink,
                    modifier = Modifier.testTag("card-source"),
                )
                Box(
                    modifier = Modifier
                        .width(stroke.hairline)
                        .height(12.dp)
                        .background(colors.ink),
                )
                Text(
                    text = bookmark.savedAt.toRelativeTime().uppercase(),
                    style = typography.captionMono,
                    color = colors.onSurfaceVariant,
                )
                if (bookmark.contentType == ContentType.Link) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Link",
                        modifier = Modifier.size(14.dp),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }

            // 1.5dp hairline separator between header and body
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(stroke.hairline)
                    .background(colors.ink),
            )

            // Body column — title, preview, optional thread, tags
            Column(
                modifier = Modifier.padding(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text = bookmark.title,
                    style = typography.displaySmall,
                    color = colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .testTag(if (bookmark.pendingDelete) "bookmark-card-strikethrough" else "card-title")
                        .brutalistStrikethrough(active = bookmark.pendingDelete, color = colors.ink),
                )
                Text(
                    text = bookmark.previewText,
                    style = typography.bodyMono,
                    color = colors.ink,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
                if (bookmark.isThread) {
                    // Accent yellow on surface fails WCAG AA at caption size in
                    // light mode (contrast ≈3.4:1). Switch to ink and keep the
                    // visual hierarchy via the ↳ glyph + uppercase mono so the
                    // indicator still reads as a "thread badge" without relying
                    // on color alone (also satisfies WCAG 1.4.1 Use of Color).
                    Text(
                        text = "↳ + ${bookmark.threadCount} MORE",
                        style = typography.captionMono,
                        color = colors.ink,
                    )
                }
                if (bookmark.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                        modifier = Modifier.testTag("card-actions"),
                    ) {
                        bookmark.tags.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .border(stroke.hairline, colors.ink, shapes.cardSmall)
                                    .background(colors.surface)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "#$tag",
                                    style = typography.tagMono,
                                    color = colors.ink,
                                )
                            }
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
        CrumbsBookmarkCard(bookmark = sampleTwitterText, onCardClick = {})
    }
}

@Preview(name = "Twitter Text Dark", showBackground = true)
@Composable
private fun PreviewTwitterTextDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsBookmarkCard(bookmark = sampleTwitterText, onCardClick = {})
    }
}

@Preview(name = "Twitter Image Light", showBackground = true)
@Composable
private fun PreviewTwitterImageLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsBookmarkCard(bookmark = sampleTwitterImage, onCardClick = {})
    }
}

@Preview(name = "Twitter Thread Light", showBackground = true)
@Composable
private fun PreviewTwitterThreadLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsBookmarkCard(bookmark = sampleTwitterThread, onCardClick = {})
    }
}

@Preview(name = "Reddit Post Light", showBackground = true)
@Composable
private fun PreviewRedditPostLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsBookmarkCard(bookmark = sampleRedditPost, onCardClick = {})
    }
}

@Preview(name = "Deleted Content Light", showBackground = true)
@Composable
private fun PreviewDeletedBookmarkLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsBookmarkCard(bookmark = sampleDeletedBookmark, onCardClick = {})
    }
}
