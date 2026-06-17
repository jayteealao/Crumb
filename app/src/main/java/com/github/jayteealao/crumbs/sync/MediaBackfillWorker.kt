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

/** Tally for one backfill sweep pass: how many tweets were processed, how many carried media back, and whether the bound was hit. */
private data class SweepResult(val processed: Int, val recovered: Int, val capped: Boolean)

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
        val entry = EntryPointAccessors.fromApplication(ctx, SyncEntryPoint::class.java)
        val uid = entry.authGateway().currentUser.value?.uid
        if (uid.isNullOrEmpty()) {
            // No signed-in user yet — do not mark done; a later launch or a fresh
            // sign-in re-enqueues (the flag is still unset, KEEP allows re-run).
            Timber.tag(TAG).d("skip uid=null")
            return Result.success()
        }
        if (isBackfillDone(ctx, uid)) {
            Timber.tag(TAG).d("already_done uid=$uid; skipping")
            return Result.success()
        }

        val syncFacade = entry.twitterSyncFacade()
        val repository = entry.repository()

        // One keyset-paginated sweep: re-fetch each tweet a page query returns, counting how
        // many actually carried data back. Bounded by MAX_BACKFILL_TWEETS. [refetch] is the
        // idempotent repair (media re-fetch, or the duplicate-safe link re-fetch).
        suspend fun sweep(
            label: String,
            page: suspend (String) -> List<String>,
            refetch: suspend (String) -> Boolean,
        ): SweepResult {
            var cursor = ""
            var processed = 0
            var recovered = 0
            while (processed < MAX_BACKFILL_TWEETS) {
                val ids = page(cursor)
                if (ids.isEmpty()) break
                for (id in ids) {
                    if (runCatching { refetch(id) }.getOrDefault(false)) {
                        recovered++
                    }
                    processed++
                }
                cursor = ids.last()
            }
            val capped = processed >= MAX_BACKFILL_TWEETS
            if (capped) {
                Timber.tag(TAG).w(
                    "backfill cap ($MAX_BACKFILL_TWEETS) reached for $label; remaining tweets " +
                        "are repaired lazily on scroll-into-view",
                )
            }
            return SweepResult(processed, recovered, capped)
        }

        suspend fun sweepLinks(): SweepResult = sweep(
            "external-link",
            { after -> syncFacade.getExternalLinkTweetsWithoutPreview(after, BATCH_SIZE) },
            { id -> repository.refetchTweetLinks(id) },
        )

        suspend fun sweepQuotes(): SweepResult = sweep(
            "quoted-body",
            { after -> syncFacade.getQuoteTweetsWithoutBody(after, BATCH_SIZE) },
            { id -> repository.refetchTweetQuotes(id) },
        )

        return try {
            // Pass 1: legacy media-less tweets (image-rendering). Pass 2: video / animated_gif
            // rows whose stream variants synced empty before the v15 column existed (inline video).
            // Pass 3: external-link tweets with no url-entity row, repaired from the server-side
            // link enrichment via refetchTweetLinks (link previews); the media sweep's
            // refetchTweetMedia early-returns for media-less tweets, so links need their own pass.
            val media = sweep(
                "media-less",
                { after -> syncFacade.getTweetsWithoutMedia(after, BATCH_SIZE) },
                { id -> repository.refetchTweetMedia(id) },
            )
            val variants = sweep(
                "variants-empty",
                { after -> syncFacade.getVideoTweetsWithoutVariants(after, BATCH_SIZE) },
                { id -> repository.refetchTweetMedia(id) },
            )
            val links = sweepLinks()
            // Pass 4: tweets that reference a quoted tweet whose body never landed
            // locally, repaired from the server-written quoted doc via refetchTweetQuotes
            // (quoted tweets); resolves the FK-free junction so the card renders the quote.
            val quotes = sweepQuotes()
            markBackfillDone(ctx, uid)
            Timber.tag(TAG).i(
                "completed media[processed=${media.processed} recovered=${media.recovered} " +
                    "capped=${media.capped}] variants[processed=${variants.processed} " +
                    "recovered=${variants.recovered} capped=${variants.capped}] " +
                    "links[processed=${links.processed} recovered=${links.recovered} capped=${links.capped}] " +
                    "quotes[processed=${quotes.processed} recovered=${quotes.recovered} capped=${quotes.capped}]",
            )
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
        // Legacy boolean flag (pre-generation): a `true` value means the original
        // backfill completed and is treated as generation 1.
        private const val KEY_DONE_PREFIX = "media_backfill_done_"
        // Current per-UID marker: the last backfill GENERATION this account completed.
        private const val KEY_GEN_PREFIX = "media_backfill_gen_"

        /**
         * The backfill generation this build requires. Bumped to 2 for the v18→v19 media
         * wipe (wrong-media-attached fix): the migration empties tweetMedia/mediaKeys, so a
         * device that already finished the original (generation-1) sweep MUST re-pull once to
         * refill them with the server-corrected attribution. Bump again to force any future
         * one-time re-pull; the lazy on-scroll re-fetch still heals visible cards meanwhile.
         */
        const val CURRENT_GENERATION = 2

        /** Legacy boolean key — kept for back-compat reads; embeds the UID per account. */
        private fun doneKey(uid: String) = "$KEY_DONE_PREFIX$uid"

        /** Current generation key — embeds the UID so each account tracks its own progress. */
        private fun genKey(uid: String) = "$KEY_GEN_PREFIX$uid"

        /**
         * The highest backfill generation this UID has completed. Prefers the explicit
         * generation int; falls back to the legacy boolean (`true` ⇒ generation 1) for installs
         * that completed before the generation marker existed; 0 when nothing has run.
         */
        private fun completedGeneration(context: Context, uid: String): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.contains(genKey(uid))) return prefs.getInt(genKey(uid), 0)
            return if (prefs.getBoolean(doneKey(uid), false)) 1 else 0
        }

        private fun isBackfillDone(context: Context, uid: String): Boolean =
            completedGeneration(context, uid) >= CURRENT_GENERATION

        private fun markBackfillDone(context: Context, uid: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(genKey(uid), CURRENT_GENERATION)
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
         * Enqueue the one-time backfill. KEEP coalesces against any worker still alive
         * from a prior process. The per-UID run-once guard is checked inside [doWork]
         * once the signed-in UID is resolved; the enqueue-site guard is omitted because
         * the UID is not available here without loading the Hilt entry-point.
         * Safe to call from app start and on fresh sign-in; wrapped so a missing
         * WorkManager (Robolectric / pre-init) cannot crash the caller.
         */
        fun enqueueOnce(context: Context) {
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
