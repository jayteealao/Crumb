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
    val videoUrl: String? = null, // Video content URL
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
)

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
