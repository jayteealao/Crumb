package com.github.jayteealao.crumbs.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * One-time media backfill for the legacy (pre-cutover) bookmark corpus. Tweets
 * collected before the cloud poll function wrote media/includes docs may have no
 * `tweetMedia` rows locally; this worker sweeps every media-less tweet and re-pulls
 * its entity set from Firestore via [com.github.jayteealao.twitter.data.Repository.refetchTweetMedia]
 * (idempotent, IGNORE-on-conflict). When media lands, Room's InvalidationTracker
 * re-emits the card with images AND completes `count-and-number`'s IMAGE type-filter
 * for those tweets.
 *
 * Runs exactly once per install: a SharedPreferences flag (set on a successful
 * sweep) short-circuits every later enqueue. Mirrors [TwitterSyncWorker]'s
 * EntryPoint + default-WorkerFactory pattern (no Hilt WorkerFactory). The sweep is
 * bounded ([MAX_BACKFILL_TWEETS]) and network-constrained so it cannot fan out
 * unboundedly; a cap hit is logged rather than silently truncated.
 */
class MediaBackfillWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (isBackfillDone(ctx)) {
            Timber.tag(TAG).d("already_done; skipping")
            return Result.success()
        }
        val entry = EntryPointAccessors.fromApplication(ctx, SyncEntryPoint::class.java)
        val uid = entry.authGateway().currentUser.value?.uid
        if (uid.isNullOrEmpty()) {
            // No signed-in user yet — do not mark done; a later launch or a fresh
            // sign-in re-enqueues (the flag is still unset, KEEP allows re-run).
            Timber.tag(TAG).d("skip uid=null")
            return Result.success()
        }

        val tweetDao = entry.tweetDao()
        val repository = entry.repository()

        return try {
            var cursor = ""
            var processed = 0
            var recovered = 0
            while (processed < MAX_BACKFILL_TWEETS) {
                val ids = tweetDao.getTweetsWithoutMedia(afterId = cursor, limit = BATCH_SIZE)
                if (ids.isEmpty()) break
                for (id in ids) {
                    if (runCatching { repository.refetchTweetMedia(id) }.getOrDefault(false)) {
                        recovered++
                    }
                    processed++
                }
                cursor = ids.last()
            }
            val capped = processed >= MAX_BACKFILL_TWEETS
            markBackfillDone(ctx)
            Timber.tag(TAG).i("completed processed=$processed recovered=$recovered capped=$capped")
            if (capped) {
                Timber.tag(TAG).w(
                    "backfill cap ($MAX_BACKFILL_TWEETS) reached; remaining media-less tweets " +
                        "are repaired lazily on scroll-into-view",
                )
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "backfill_failed attempt=$runAttemptCount")
            // Do NOT mark done on failure — retry with backoff, then give up (the
            // lazy on-view path still repairs cards as they are viewed).
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val TAG = "MediaBackfill"
        const val UNIQUE_NAME = "twitter-media-backfill"
        const val MAX_RETRY_ATTEMPTS = 3
        const val BATCH_SIZE = 25
        // One-time bound on Firestore fan-out: each tweet re-fetch is a full entity
        // pull, so this caps the worst-case read amplification for a large corpus.
        const val MAX_BACKFILL_TWEETS = 500

        private const val PREFS_NAME = "media_backfill_prefs"
        private const val KEY_DONE = "media_backfill_done"

        private fun isBackfillDone(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_DONE, false)

        private fun markBackfillDone(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DONE, true)
                .apply()
        }

        private fun buildRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<MediaBackfillWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

        /**
         * Enqueue the one-time backfill if it has never completed. No-op once the
         * run-once flag is set. KEEP coalesces against any worker still alive from a
         * prior process. Safe to call from app start and on fresh sign-in; wrapped so
         * a missing WorkManager (Robolectric / pre-init) cannot crash the caller.
         */
        fun enqueueOnce(context: Context) {
            if (isBackfillDone(context)) return
            try {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    UNIQUE_NAME,
                    ExistingWorkPolicy.KEEP,
                    buildRequest(),
                )
                Timber.tag(TAG).d("enqueue_ok")
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "enqueue_failed (likely test env or pre-init)")
            }
        }
    }
}
