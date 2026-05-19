package com.github.jayteealao.crumbs.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Single-flight token-refresh primitive shared by every provider that needs
 * "refresh-first auth recovery". Two callers landing on the same expired
 * token at once will serialize on [mutex]; the second observes the actual
 * outcome of the first refresh instead of skipping with a false "true".
 *
 * Use it like:
 *
 * ```
 * private val refreshMutex = Mutex()
 *
 * private suspend fun refreshTokenSingleFlight(seed: String): Boolean =
 *     withAuthRefreshSingleFlight(refreshMutex, tag = "twitter-refresh") {
 *         val token = prefs.refreshCode.first().ifBlank { seed }
 *         val response = authClient.refreshAccessToken(token) ?: return@withAuthRefreshSingleFlight false
 *         prefs.setAccessAndRefreshToken(response.accessToken!!, response.refreshToken!!)
 *         true
 *     }
 * ```
 *
 * The wrapper is responsible for: holding the mutex, catching exceptions,
 * and emitting consistent log lines. The caller's [doRefresh] body owns the
 * actual network call and token persistence and must return `true` on
 * success, `false` on a recoverable failure (null/blank tokens).
 */
suspend fun withAuthRefreshSingleFlight(
    mutex: Mutex,
    tag: String,
    doRefresh: suspend () -> Boolean,
): Boolean = mutex.withLock {
    try {
        val ok = doRefresh()
        if (ok) {
            Timber.d("$tag: token refreshed and persisted")
        } else {
            Timber.w("$tag: refresh returned null/blank tokens")
        }
        ok
    } catch (e: Exception) {
        Timber.e(e, "$tag: exception during refresh")
        false
    }
}
