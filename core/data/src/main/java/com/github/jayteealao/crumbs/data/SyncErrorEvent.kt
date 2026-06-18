package com.github.jayteealao.crumbs.data

import com.github.jayteealao.crumbs.models.BookmarkSource

sealed interface SyncErrorEvent {
    val source: BookmarkSource

    data class TwitterAuth401(val message: String = "Twitter session expired") : SyncErrorEvent {
        override val source: BookmarkSource = BookmarkSource.Twitter
    }

    data class RedditAuth401(val message: String = "Reddit session expired") : SyncErrorEvent {
        override val source: BookmarkSource = BookmarkSource.Reddit
    }

    data class Other(override val source: BookmarkSource, val message: String) : SyncErrorEvent
}
