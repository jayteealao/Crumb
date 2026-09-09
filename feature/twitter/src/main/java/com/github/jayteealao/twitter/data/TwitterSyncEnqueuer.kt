package com.github.jayteealao.twitter.data

import kotlinx.coroutines.flow.Flow

/**
 * Hilt-bound seam that lets [Repository] (in `feature/twitter`) enqueue the
 * `TwitterSyncWorker` (in `app/`) without taking a direct module dependency.
 * The app-side implementation knows about WorkManager + the worker class; the
 * feature module only knows it can ask for a sync to be scheduled.
 *
 * Implementations resolve the current Firebase Auth uid internally and no-op
 * if the user is not signed in — callers do not need to inject auth state.
 */
interface TwitterSyncEnqueuer {
    /**
     * Cold-start / first-sign-in backfill. The worker promotes itself to a
     * foreground service for this request so the OS does not kill the
     * potentially long-running drain.
     */
    fun enqueueColdStart()

    /**
     * Pull-to-refresh and "user is right here, keep it light" path. Stays as
     * expedited work without the foreground promotion.
     */
    fun enqueueRefresh()

    /**
     * Emits true while the unique-by-uid worker is `ENQUEUED` or `RUNNING`,
     * false otherwise (including when no work has ever been enqueued or when
     * the user is signed out). Powers `Repository.isRefreshing` so the UI
     * spinner stays up across backgrounding + activity recreation.
     */
    fun observeIsRunning(): Flow<Boolean>
}
