package com.github.jayteealao.crumbs.data

sealed interface SyncErrorEvent {
    val source: String

    data class TwitterAuth401(val message: String = "Twitter session expired") : SyncErrorEvent {
        override val source: String = BookmarkSource.TWITTER
    }

    data class RedditAuth401(val message: String = "Reddit session expired") : SyncErrorEvent {
        override val source: String = BookmarkSource.REDDIT
    }

    data class Other(override val source: String, val message: String) : SyncErrorEvent
}
