package com.github.jayteealao.twitter.data

import com.github.jayteealao.twitter.data.dto.SyncStatus
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads `users/{uid}/sync_status/state` from Firestore. One-shot per refresh —
 * no live listener — and throttled to one server round-trip every 5 seconds so
 * rapid foreground transitions do not hammer Firestore.
 *
 * On offline or timeout the last good value is retained; `null` only means the
 * doc has not been read yet for the current user.
 */
@Singleton
class SyncStatusRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    private val _flow = MutableStateFlow<SyncStatus?>(null)
    val flow: StateFlow<SyncStatus?> = _flow.asStateFlow()

    @Volatile
    private var lastFetchMs: Long = 0L

    // Serializes the throttle check-then-fetch so two concurrent callers that
    // both clear the throttle window do not each issue a server round-trip.
    private val refreshMutex = Mutex()

    /**
     * Returns the current `SyncStatus` for the signed-in user. If [force] is
     * `false` and the last successful fetch was within [THROTTLE_MS], returns
     * the cached value without a network round-trip.
     */
    suspend fun refresh(force: Boolean = false): SyncStatus? {
        val uid = auth.currentUser?.uid ?: run {
            _flow.value = null
            return null
        }

        if (!force && isWithinThrottle()) {
            return _flow.value
        }

        return refreshMutex.withLock {
            // Re-check inside the lock: a concurrent caller may have completed a
            // fetch while we waited, so skip the duplicate server round-trip.
            if (!force && isWithinThrottle()) {
                return@withLock _flow.value
            }
            try {
                val snapshot = withTimeout(READ_TIMEOUT_MS) {
                    firestore.collection("users")
                        .document(uid)
                        .collection("sync_status")
                        .document("state")
                        .get(Source.SERVER)
                        .await()
                }
                val parsed = parse(snapshot.data)
                _flow.value = parsed
                lastFetchMs = System.currentTimeMillis()
                parsed
            } catch (e: TimeoutCancellationException) {
                Timber.w(e, "sync_status read timed out; keeping cached value")
                _flow.value
            } catch (e: Exception) {
                Timber.w(e, "sync_status read failed (offline?); keeping cached value")
                _flow.value
            }
        }
    }

    private fun isWithinThrottle(): Boolean {
        val now = System.currentTimeMillis()
        return lastFetchMs != 0L && now - lastFetchMs < THROTTLE_MS
    }

    private fun parse(data: Map<String, Any?>?): SyncStatus {
        if (data == null) return SyncStatus()
        return SyncStatus(
            linked = data["linked"] as? Boolean ?: false,
            lastPolledAt = data["lastPolledAt"] as? Timestamp,
            lastError = data["lastError"] as? String,
            itemsAdded = (data["itemsAdded"] as? Number)?.toInt(),
            xUserId = data["xUserId"] as? String,
            latestTweetId = data["latest_tweet_id"] as? String,
        )
    }

    /**
     * Debug-only mutator used by Maestro pre-seeds (`debug_action=seed_sync_status`).
     * Bypasses Firestore — pushes a synthesized `SyncStatus` straight into the
     * flow + sets the throttle clock so a subsequent foreground refresh does
     * not immediately wipe it. Production callers MUST NOT invoke this.
     */
    fun seedForDebug(status: SyncStatus?) {
        _flow.value = status
        lastFetchMs = System.currentTimeMillis()
    }

    companion object {
        const val THROTTLE_MS = 5_000L
        private const val READ_TIMEOUT_MS = 8_000L
    }
}
