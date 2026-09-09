package com.github.jayteealao.twitter.data.dto

import com.google.firebase.Timestamp

/**
 * Mirror of the server-written `users/{uid}/sync_status/state` document.
 * Server-internal fields (`poll_lease`) are intentionally omitted.
 */
data class SyncStatus(
    val linked: Boolean = false,
    val lastPolledAt: Timestamp? = null,
    val lastError: String? = null,
    val itemsAdded: Int? = null,
    val xUserId: String? = null,
    val latestTweetId: String? = null,
)
