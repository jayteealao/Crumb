package com.github.jayteealao.crumbs.models

/**
 * Unified bookmark model for Crumbs v2.0
 * Supports Twitter and Reddit content
 */
data class Bookmark(
    val id: String,
    val source: BookmarkSource,
    val author: String, // @username or u/username
    val title: String, // First line or extracted title
    val previewText: String, // 5-6 lines of content
    val imageUrl: String? = null,
    // All photo URLs for the post, in order. [imageUrl] stays the primary/single
    // back-compat URL (== imageUrls.firstOrNull()); this list drives the card's
    // 2×2 image grid and the full-screen viewer's pager. Defaults empty so Reddit
    // and every other caller are unaffected (mirrors the dbNumber precedent).
    val imageUrls: List<String> = emptyList(),
    val videoUrl: String? = null, // Video content URL (best progressive/HLS URL; back-compat single field)
    // Poster frame shown under the play badge before playback, and on the inline
    // player's shutter until the first video frame renders. Defaults null (Reddit
    // and non-video cards never set it).
    val videoThumbnailUrl: String? = null,
    // Ordered HLS / DASH / progressive stream variants for a video or animated_gif
    // tweet. The inline player selects HLS first, then DASH, then highest-bitrate
    // MP4 (see VariantSelection). A small core-models value type so core/designsystem
    // can build MediaItems without depending on feature/twitter. Defaults empty
    // (Reddit + every non-video caller), mirroring the imageUrls precedent.
    val videoVariants: List<VideoVariant> = emptyList(),
    // Outbound-link preview fields, set only for an external-link tweet (the
    // first non-twitter/x.com URL entity). [linkUrl] is the destination the
    // preview surface opens in the external browser; [linkDisplayUrl] is the
    // domain/short label; [linkTitle]/[linkDescription]/[linkImageUrl] are the
    // server-enriched OpenGraph metadata (any may be null → the card degrades to
    // a URL-only chip). All default null so Reddit + non-link cards are
    // unaffected (mirrors the imageUrls / videoVariants precedent).
    val linkUrl: String? = null,
    val linkDisplayUrl: String? = null,
    val linkTitle: String? = null,
    val linkDescription: String? = null,
    val linkImageUrl: String? = null,
    val contentType: ContentType,
    val savedAt: Long, // Timestamp when bookmark was saved
    val tags: List<String> = emptyList(),

    // Thread-specific fields
    val isThread: Boolean = false,
    val threadCount: Int = 1, // Number of tweets in thread
    val threadExpanded: Boolean = false,

    // Status fields
    val isDeleted: Boolean = false, // Original source deleted/unavailable
    val isRead: Boolean = false, // User has opened this
    // Server-side flag from daily-poll: X has removed this bookmark and the
    // user has not yet confirmed/cancelled. Drives strikethrough rendering
    // and swipe affordances on Twitter rows; always `false` for Reddit.
    val pendingDelete: Boolean = false,

    // Original source URL
    val sourceUrl: String,

    // Source engagement count (likes/score). `null` when not yet wired from
    // the data layer; rendered as part of the meta row (e.g. "IMAGE · ↑ 2.4k")
    // and degrades gracefully to type-only when absent.
    val engagementCount: Int? = null,

    // Display-only "number in the DB" shown in each card's index strip. For
    // Twitter this is the SQLite rowid surfaced by the feed query; rendered
    // zero-padded (`%03d`) via the card's indexOverride. `0L` for sources that
    // do not surface it (Reddit), which renders as the legacy `000`. Not an
    // identifier — the rowid can change under VACUUM/migration.
    val dbNumber: Long = 0L,
) {
    companion object {
        /**
         * Sentinel [savedAt] value meaning "no parseable timestamp was available". Renders as
         * the `_` marker (never a fabricated "now") and sorts last because it is smaller than any
         * real epoch-millis. `Long.MIN_VALUE` cannot collide with Reddit's `createdUtc * 1000`,
         * which is always positive.
         */
        const val UNKNOWN_TIME: Long = Long.MIN_VALUE
    }
}

/**
 * Bookmark source platform
 */
enum class BookmarkSource {
    Twitter,
    Reddit;

    fun displayName(): String = when (this) {
        Twitter -> "Twitter"
        Reddit -> "Reddit"
    }
}

/**
 * Type of content in the bookmark
 */
enum class ContentType {
    Text,      // Text-only post
    Image,     // Post with image(s)
    Video,     // Post with video
    Link,      // Post with external link
    Thread;    // Twitter thread

    fun iconDescription(): String = when (this) {
        Text -> "Text post"
        Image -> "Image post"
        Video -> "Video post"
        Link -> "Link post"
        Thread -> "Thread"
    }
}

/**
 * Helper function to format relative timestamps
 */
fun Long.toRelativeTime(): String {
    if (this == Bookmark.UNKNOWN_TIME) return "_"
    val now = System.currentTimeMillis()
    val diff = now - this

    return when {
        diff < 60_000 -> "just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        diff < 2592000_000 -> "${diff / 604800_000}w ago"
        else -> "${diff / 2592000_000}mo ago"
    }
}
