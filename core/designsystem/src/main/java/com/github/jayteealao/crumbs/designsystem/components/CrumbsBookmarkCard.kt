package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography
import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.models.ContentType
import com.github.jayteealao.crumbs.models.toRelativeTime

/**
 * Unified bookmark card component for Twitter and Reddit content
 *
 * Polymorphic card that adapts its display based on the bookmark's content type:
 * - Text: Text-only display
 * - Image: Full-width image at top
 * - Video: Full-width video player at top
 * - Link: Link icon indicator in metadata
 * - Thread: ThreadIndicator showing "+ N more"
 *
 * Supports tap to open and long-press for quick actions.
 *
 * @param bookmark The bookmark data to display
 * @param onCardClick Callback when card is tapped (receives sourceUrl)
 * @param onLongPress Callback when card is long-pressed (for quick actions)
 * @param isExpanded Whether thread is expanded (future feature)
 * @param modifier Modifier to be applied to the component
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun CrumbsBookmarkCard(
    bookmark: Bookmark,
    onCardClick: (String) -> Unit,
    onLongPress: (Bookmark) -> Unit = {},
    isExpanded: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val typography = LocalCrumbsTypography.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onCardClick(bookmark.sourceUrl) },
                onLongClick = { onLongPress(bookmark) }
            ),
        shape = CrumbsShapes.card, // bottom-end cut (12dp)
        color = colors.surface,
        border = BorderStroke(1.dp, colors.textSecondary.copy(alpha = 0.1f))
    ) {
        Box {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Full-width media (image or video) at top
                when {
                    bookmark.contentType == ContentType.Image && bookmark.imageUrl != null -> {
                        AsyncImage(
                            model = bookmark.imageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                    bookmark.contentType == ContentType.Video -> {
                        val videoUrl = bookmark.videoUrl
                        if (videoUrl != null) {
                            CrumbsVideoPlayer(
                                videoUrl = videoUrl,
                                thumbnailUrl = bookmark.imageUrl,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Content column
                Column(
                    modifier = Modifier.padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.md)
                ) {
                    // Metadata row: source icon + author + timestamp + content type
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Source icon
                        Icon(
                            painter = painterResource(
                                id = when (bookmark.source) {
                                    BookmarkSource.Twitter -> com.github.jayteealao.crumbs.designsystem.R.drawable.fatwitter
                                    BookmarkSource.Reddit -> com.github.jayteealao.crumbs.designsystem.R.drawable.fareddit
                                }
                            ),
                            contentDescription = bookmark.source.displayName(),
                            modifier = Modifier.size(20.dp),
                            tint = colors.textSecondary
                        )

                        // Author
                        Text(
                            text = bookmark.author,
                            style = typography.bodyMedium,
                            color = colors.textSecondary
                        )

                        // Separator
                        Text(
                            text = "•",
                            style = typography.bodyMedium,
                            color = colors.textSecondary
                        )

                        // Timestamp
                        Text(
                            text = bookmark.savedAt.toRelativeTime(),
                            style = typography.bodyMedium,
                            color = colors.textSecondary
                        )

                        // Content type indicator
                        if (bookmark.contentType == ContentType.Link) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = "Link",
                                modifier = Modifier.size(16.dp),
                                tint = colors.textSecondary
                            )
                        }
                    }

                    // Title
                    Text(
                        text = bookmark.title,
                        style = typography.titleLarge,
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Preview text
                    Text(
                        text = bookmark.previewText,
                        style = typography.bodyLarge,
                        color = colors.textPrimary,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Thread indicator (if this is a thread)
                    if (bookmark.isThread) {
                        ThreadIndicator(
                            threadCount = bookmark.threadCount,
                            onClick = { /* TODO: Expand thread inline */ }
                        )
                    }

                    // Tags (if present)
                    if (bookmark.tags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm)
                        ) {
                            bookmark.tags.forEach { tag ->
                                CrumbsTagChip(label = tag)
                            }
                        }
                    }
                }
            }

            // Deleted overlay (if content is deleted)
            if (bookmark.isDeleted) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.surface.copy(alpha = 0.97f)), // Increased opacity for better contrast
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Content unavailable",
                        style = typography.bodyMedium,
                        color = colors.textPrimary // Changed from textSecondary for WCAG AA compliance
                    )
                }
            }
        }
    }
}

// Sample bookmark data for previews
private val sampleTwitterText = Bookmark(
    id = "1",
    source = BookmarkSource.Twitter,
    author = "@designpatterns",
    title = "Understanding SOLID Principles",
    previewText = "Let me explain the five SOLID principles that every developer should know. These fundamental concepts will help you write better, more maintainable code.",
    contentType = ContentType.Text,
    savedAt = System.currentTimeMillis() - 3600000, // 1 hour ago
    tags = listOf("programming", "design"),
    sourceUrl = "https://twitter.com/i/web/status/123"
)

private val sampleTwitterImage = Bookmark(
    id = "2",
    source = BookmarkSource.Twitter,
    author = "@kotlinconf",
    title = "Compose Multiplatform is here!",
    previewText = "Excited to announce the stable release of Compose Multiplatform. Build beautiful UIs for Android, iOS, Desktop, and Web.",
    imageUrl = "https://example.com/image.jpg",
    contentType = ContentType.Image,
    savedAt = System.currentTimeMillis() - 7200000, // 2 hours ago
    tags = listOf("kotlin", "compose", "multiplatform"),
    sourceUrl = "https://twitter.com/i/web/status/124"
)

private val sampleTwitterThread = Bookmark(
    id = "3",
    source = BookmarkSource.Twitter,
    author = "@architectpatterns",
    title = "Clean Architecture Thread",
    previewText = "1/ Let's talk about Clean Architecture and why it matters for modern Android development...",
    contentType = ContentType.Thread,
    savedAt = System.currentTimeMillis() - 86400000, // 1 day ago
    tags = listOf("architecture", "android"),
    isThread = true,
    threadCount = 12,
    sourceUrl = "https://twitter.com/i/web/status/125"
)

private val sampleRedditPost = Bookmark(
    id = "4",
    source = BookmarkSource.Reddit,
    author = "u/androiddev",
    title = "Tips for optimizing RecyclerView performance",
    previewText = "Here are some lesser-known tips for getting better performance out of RecyclerView. These helped me reduce jank significantly in my production app.",
    contentType = ContentType.Text,
    savedAt = System.currentTimeMillis() - 172800000, // 2 days ago
    tags = listOf("android", "performance"),
    sourceUrl = "https://reddit.com/r/androiddev/comments/abc123"
)

private val sampleDeletedBookmark = Bookmark(
    id = "5",
    source = BookmarkSource.Twitter,
    author = "@deleteduser",
    title = "This tweet has been deleted",
    previewText = "This content is no longer available.",
    contentType = ContentType.Text,
    savedAt = System.currentTimeMillis() - 604800000, // 1 week ago
    isDeleted = true,
    sourceUrl = "https://twitter.com/i/web/status/126"
)

// Previews
@Preview(name = "Twitter Text - Light", showBackground = true)
@Composable
private fun PreviewTwitterTextLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsBookmarkCard(
            bookmark = sampleTwitterText,
            onCardClick = {}
        )
    }
}

@Preview(name = "Twitter Image - Light", showBackground = true)
@Composable
private fun PreviewTwitterImageLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsBookmarkCard(
            bookmark = sampleTwitterImage,
            onCardClick = {}
        )
    }
}

@Preview(name = "Twitter Thread - Light", showBackground = true)
@Composable
private fun PreviewTwitterThreadLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsBookmarkCard(
            bookmark = sampleTwitterThread,
            onCardClick = {}
        )
    }
}

@Preview(name = "Reddit Post - Light", showBackground = true)
@Composable
private fun PreviewRedditPostLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsBookmarkCard(
            bookmark = sampleRedditPost,
            onCardClick = {}
        )
    }
}

@Preview(name = "Deleted Content - Light", showBackground = true)
@Composable
private fun PreviewDeletedBookmarkLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsBookmarkCard(
            bookmark = sampleDeletedBookmark,
            onCardClick = {}
        )
    }
}

@Preview(name = "Twitter Text - Dark", showBackground = true)
@Composable
private fun PreviewTwitterTextDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsBookmarkCard(
            bookmark = sampleTwitterText,
            onCardClick = {}
        )
    }
}
