package com.github.jayteealao.twitter.data

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around Firebase Callable Functions for Twitter/X server-side operations.
 *
 * Extracted from [Repository] to give the callable-function surface its own class, keeping
 * [Repository] focused on local DB / paging / tag concerns.
 */
@Singleton
class TwitterCallableService @Inject constructor(
    private val functions: FirebaseFunctions,
) {

    /**
     * Invokes the `triggerPoll` server-side callable. Returns the raw response payload,
     * or `null` if the call failed (the exception is swallowed and logged by the caller
     * wrapping this in [runCatching]).
     *
     * Intentionally throws on network / Firebase error so callers can use [runCatching].
     */
    suspend fun triggerPoll(): Map<*, *>? {
        val result = functions
            .getHttpsCallable("triggerPoll")
            .call(emptyMap<String, Any>())
            .await()
        return result.data as? Map<*, *>
    }

    /**
     * Invokes the `disconnectX` server-side callable. Throws [IllegalStateException] if
     * the server responds with `{ok: false}` so callers can treat it as a [Result] failure.
     */
    suspend fun disconnectX(): Unit {
        val response = functions
            .getHttpsCallable("disconnectX")
            .call(emptyMap<String, Any>())
            .await()
        val payload = response.data as? Map<*, *>
        val ok = payload?.get("ok") as? Boolean ?: false
        if (!ok) {
            throw IllegalStateException(payload?.get("reason") as? String ?: "disconnect_failed")
        }
        Timber.d("TwitterCallableService: disconnectX succeeded")
    }
}
