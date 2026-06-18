package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.media3.common.Player
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
    indexOverride: String? = null,
    // Tapping a card image reports its index within bookmark.imageUrls so the
    // caller can open the full-screen viewer at that page. Default no-op keeps
    // the image inert for callers that do not host a viewer (Reddit, previews).
    onImageClick: (index: Int) -> Unit = {},
    // Inline video. videoPlayer is the SHARED ExoPlayer, passed non-null ONLY when this
    // card is the single active video (and the full-screen viewer is closed); null leaves
    // the band on its poster + play badge. onVideoPlay asks the host to make this card the
    // active video; onVideoExpand opens the full-screen viewer. All default inert for
    // non-video callers (Reddit, previews).
    videoPlayer: Player? = null,
    onVideoPlay: () -> Unit = {},
    onVideoExpand: () -> Unit = {},
    // Tapping a card's link preview reports the outbound URL so the caller can
    // open it in the external browser. Default no-op for cards without a link
    // surface (Reddit, image/video/text). The rest of the card keeps onCardClick.
    onLinkClick: (String) -> Unit = {},
    // Tapping a card's quoted sub-card reports the quoted tweet's permalink so the
    // caller can open it in the external browser. Default no-op for non-quote cards.
    // The rest of the card keeps onCardClick (the parent permalink).
    onQuoteClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (bookmark.pendingDelete) {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        onCancelDeletePending?.invoke(bookmark.id)
                        true
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        onConfirmDeletePending?.invoke(bookmark.id)
                        true
                    }
                    SwipeToDismissBoxValue.Settled -> false
                }
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
                indexOverride = indexOverride,
                onImageClick = onImageClick,
                videoPlayer = videoPlayer,
                onVideoPlay = onVideoPlay,
                onVideoExpand = onVideoExpand,
                onLinkClick = onLinkClick,
                onQuoteClick = onQuoteClick,
            )
        }
    } else {
        BookmarkCardContent(
            bookmark = bookmark,
            onCardClick = onCardClick,
            onLongPress = onLongPress,
            index = index,
            indexOverride = indexOverride,
            onImageClick = onImageClick,
            videoPlayer = videoPlayer,
            onVideoPlay = onVideoPlay,
            onVideoExpand = onVideoExpand,
            onLinkClick = onLinkClick,
            onQuoteClick = onQuoteClick,
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
    indexOverride: String? = null,
    onImageClick: (index: Int) -> Unit = {},
    videoPlayer: Player? = null,
    onVideoPlay: () -> Unit = {},
    onVideoExpand: () -> Unit = {},
    onLinkClick: (String) -> Unit = {},
    onQuoteClick: (String) -> Unit = {},
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
            BookmarkCardMedia(
                imageUrl = bookmark.imageUrl,
                imageUrls = bookmark.imageUrls,
                videoThumbnailUrl = bookmark.videoThumbnailUrl,
                contentType = bookmark.contentType,
                onImageClick = onImageClick,
                videoPlayer = videoPlayer,
                onVideoPlay = onVideoPlay,
                onVideoExpand = onVideoExpand,
                linkTitle = bookmark.linkTitle,
                linkDescription = bookmark.linkDescription,
                linkDisplayUrl = bookmark.linkDisplayUrl,
                linkImageUrl = bookmark.linkImageUrl,
                linkUrl = bookmark.linkUrl,
                onLinkClick = onLinkClick,
            )

            // Quoted sub-card — rendered as a sibling slot AFTER the media so it
            // co-exists with any media (BookmarkCardMedia early-returns per
            // contentType, so the quote must live outside it to stay orthogonal). A
            // referenced quote with no body renders the "unavailable" placeholder.
            if (bookmark.quotedTweetId != null) {
                BookmarkCardQuotedTweet(
                    authorName = bookmark.quotedAuthorName,
                    authorHandle = bookmark.quotedAuthorHandle,
                    text = bookmark.quotedText,
                    isUnavailable = bookmark.quotedText == null,
                    onClick = { bookmark.quotedTweetUrl?.let(onQuoteClick) },
                )
            }

            // CrumbsIndexStrip header replaces the inline source/author/age row.
            // `indexOverride` lets callers swap the default `%03d` numeral for a
            // context-specific label such as `HIT/03` (search results) or
            // `THREAD · 12 ↕` (thread root card). Default behavior preserved
            // when null.
            CrumbsIndexStrip(
                index = indexOverride ?: "%03d".format(index),
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
                modifier = Modifier.padding(spacing.cardContentInset),
            ) {
                BookmarkCardBody(bookmark = bookmark)
                Spacer(Modifier.height(spacing.sm))
                BookmarkCardFooter(bookmark = bookmark)
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

/**
 * Optional media banner at the top of the card.
 *
 * - **Video** ([contentType] == Video): the inline [CrumbsVideoPlayer] in the 16:7 band —
 *   poster + brutalist play badge until tapped, the shared [videoPlayer] when this card is
 *   the active video, plus an expand affordance. Rendered only when a poster or an attached
 *   player exists; a variants-less, thumbnail-less video card degrades to text-only (its
 *   variants repair via the lazy re-fetch + backfill sweep).
 * - **Image** ([contentType] == Image): a single image fills the 16:7 band; two or more
 *   photos render as a 2×2 grid (see [BookmarkCardImageGrid]) with a "+N" overflow on the
 *   fourth tile. Each image shows the accent loading placeholder until it settles and reports
 *   taps via [onImageClick]. [imageUrls] is preferred; [imageUrl] is the back-compat single URL.
 * - **Link** ([contentType] == Link): a brutalist [BookmarkCardLinkPreview] panel — an optional
 *   OG image band, the link title (or domain when absent), and an accent domain line. Tapping it
 *   reports [linkUrl] via [onLinkClick] (the destination opens in the external browser); a link
 *   with no obtainable metadata degrades to a URL-only chip.
 */
@Composable
private fun BookmarkCardMedia(
    imageUrl: String?,
    imageUrls: List<String>,
    videoThumbnailUrl: String?,
    contentType: ContentType,
    onImageClick: (index: Int) -> Unit,
    videoPlayer: Player?,
    onVideoPlay: () -> Unit,
    onVideoExpand: () -> Unit,
    linkTitle: String?,
    linkDescription: String?,
    linkDisplayUrl: String?,
    linkImageUrl: String?,
    linkUrl: String?,
    onLinkClick: (String) -> Unit,
) {
    val colors = LocalCrumbsColors.current
    val stroke = LocalCrumbsStroke.current

    if (contentType == ContentType.Link) {
        BookmarkCardLinkPreview(
            title = linkTitle,
            description = linkDescription,
            displayUrl = linkDisplayUrl,
            imageUrl = linkImageUrl,
            onClick = { linkUrl?.let(onLinkClick) },
        )
        return
    }

    if (contentType == ContentType.Video) {
        val poster = videoThumbnailUrl ?: imageUrl ?: imageUrls.firstOrNull()
        if (poster == null && videoPlayer == null) return
        CrumbsVideoPlayer(
            posterUrl = poster,
            player = videoPlayer,
            onPlayClick = onVideoPlay,
            onExpand = onVideoExpand,
        )
        MediaHairline(color = colors.ink, height = stroke.hairline)
        return
    }

    val images = when {
        imageUrls.isNotEmpty() -> imageUrls
        imageUrl != null -> listOf(imageUrl)
        else -> emptyList()
    }
    if (images.isEmpty() || contentType != ContentType.Image) return

    if (images.size == 1) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 7f)
                .testTag("bookmark-card-image")
                .clickable { onImageClick(0) },
        ) {
            CrumbsCardMediaImage(
                url = images[0],
                contentDescription = "Image",
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        BookmarkCardImageGrid(images = images, onImageClick = onImageClick)
    }
    MediaHairline(color = colors.ink, height = stroke.hairline)
}

/** 1dp hairline separator drawn below the media band. */
@Composable
private fun MediaHairline(color: androidx.compose.ui.graphics.Color, height: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(color),
    )
}

/**
 * Brutalist outbound-link preview panel rendered in the card's media slot for a
 * [ContentType.Link] bookmark. An ink-bordered [shapes.card] (RectangleShape)
 * panel inset by the body padding: an optional OG image band (accent loading
 * placeholder via [CrumbsCardMediaImage]) when [imageUrl] is present, then the
 * [title] (or [displayUrl] when the title is absent) in mono, an optional
 * [description], and the [displayUrl] domain in the accent. The whole panel is
 * tappable — [onClick] opens the destination in the external browser.
 */
@Composable
private fun BookmarkCardLinkPreview(
    title: String?,
    description: String?,
    displayUrl: String?,
    imageUrl: String?,
    onClick: () -> Unit,
) {
    val colors = LocalCrumbsColors.current
    val stroke = LocalCrumbsStroke.current
    val shapes = LocalCrumbsShapes.current
    val spacing = LocalCrumbsSpacing.current
    val typography = LocalCrumbsTypography.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(spacing.cardContentInset),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(stroke.regular, colors.ink, shapes.card)
                .testTag("bookmark-card-link-preview")
                .clickable { onClick() },
        ) {
            if (imageUrl != null) {
                CrumbsCardMediaImage(
                    url = imageUrl,
                    contentDescription = "Link preview image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 7f),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(stroke.hairline)
                        .background(colors.ink),
                )
            }
            Column(modifier = Modifier.padding(spacing.sm + 2.dp)) { // 10dp inner
                Text(
                    text = title ?: displayUrl ?: "",
                    style = typography.bodyMono,
                    color = colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("link-preview-title"),
                )
                if (!description.isNullOrBlank()) {
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = description,
                        style = typography.metaMono,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!displayUrl.isNullOrBlank()) {
                    Spacer(Modifier.height(spacing.sm))
                    Text(
                        text = displayUrl.uppercase(),
                        style = typography.metaMono,
                        color = colors.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Brutalist quoted-tweet sub-card. An ink-bordered [shapes.card] (RectangleShape)
 * panel inset by the body padding, modeled on [BookmarkCardLinkPreview]: an author
 * line ([authorName] [authorHandle]) in the accent, then the quoted [text] in mono
 * (clamped to 4 lines). When [isUnavailable] (a quote was referenced but its body is
 * gone — deleted/protected) the panel renders a single placeholder line instead. The
 * whole panel is tappable — [onClick] opens the quoted tweet's permalink in the
 * external browser; the non-consuming gesture leaves the rest of the card's tap intact.
 */
@Composable
private fun BookmarkCardQuotedTweet(
    authorName: String?,
    authorHandle: String?,
    text: String?,
    isUnavailable: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalCrumbsColors.current
    val stroke = LocalCrumbsStroke.current
    val shapes = LocalCrumbsShapes.current
    val spacing = LocalCrumbsSpacing.current
    val typography = LocalCrumbsTypography.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(spacing.cardContentInset),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(stroke.regular, colors.ink, shapes.card)
                .testTag("bookmark-card-quoted-tweet")
                .clickable { onClick() }
                .padding(spacing.sm + 2.dp), // 10dp inner
        ) {
            if (isUnavailable) {
                Text(
                    text = "QUOTED TWEET UNAVAILABLE",
                    style = typography.metaMono,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("quoted-tweet-unavailable"),
                )
                return@Column
            }
            val author = listOfNotNull(authorName, authorHandle).joinToString(" ").trim()
            if (author.isNotBlank()) {
                Text(
                    text = author,
                    style = typography.metaMono,
                    color = colors.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("quoted-tweet-author"),
                )
                Spacer(Modifier.height(spacing.xs))
            }
            Text(
                text = text.orEmpty(),
                style = typography.bodyMono,
                color = colors.ink,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("quoted-tweet-text"),
            )
        }
    }
}

/**
 * Title and preview-text section of the card, including the optional thread
 * continuation indicator ("↳ + N MORE").
 */
@Composable
private fun BookmarkCardBody(bookmark: Bookmark) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val typography = LocalCrumbsTypography.current

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
}

/**
 * Footer section: dashed divider, engagement meta row, and tag chip FlowRow.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookmarkCardFooter(bookmark: Bookmark) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val stroke = LocalCrumbsStroke.current
    val typography = LocalCrumbsTypography.current

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

private val sampleTwitterLink = Bookmark(
    id = "6",
    source = BookmarkSource.Twitter,
    author = "@reader",
    title = "Worth a read on brutalist design",
    previewText = "Sharing this great piece on raw, honest web interfaces. https://t.co/abc",
    contentType = ContentType.Link,
    savedAt = System.currentTimeMillis() - 5400000,
    sourceUrl = "https://twitter.com/i/web/status/127",
    linkUrl = "https://brutalist-web.design/",
    linkDisplayUrl = "brutalist-web.design",
    linkTitle = "Guidelines for Brutalist Web Design",
    linkDescription = "Raw content, honest materials, and a focus on the reader over decoration.",
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

@Preview(name = "Twitter Link Light", showBackground = true)
@Composable
private fun PreviewTwitterLinkLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsBookmarkCard(bookmark = sampleTwitterLink, onCardClick = {}, index = 6)
    }
}

private val sampleTwitterQuote = Bookmark(
    id = "7",
    source = BookmarkSource.Twitter,
    author = "@commenter",
    title = "Adding my take on this",
    previewText = "This thread completely reframed how I think about it. Worth a full read.",
    contentType = ContentType.Text,
    savedAt = System.currentTimeMillis() - 4500000,
    sourceUrl = "https://twitter.com/i/web/status/128",
    quotedTweetId = "999",
    quotedText = "The original insight everyone is quoting: simplicity scales, cleverness doesn't.",
    quotedAuthorName = "Original Author",
    quotedAuthorHandle = "@original",
    quotedTweetUrl = "https://twitter.com/original/status/999",
)

@Preview(name = "Twitter Quote Light", showBackground = true)
@Composable
private fun PreviewTwitterQuoteLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsBookmarkCard(bookmark = sampleTwitterQuote, onCardClick = {}, index = 7)
    }
}
