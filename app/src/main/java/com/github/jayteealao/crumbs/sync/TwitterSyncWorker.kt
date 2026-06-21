package com.github.jayteealao.crumbs.sync

import android.content.Context
import android.os.Build
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.jayteealao.crumbs.auth.AuthGateway
import com.github.jayteealao.crumbs.data.DeletedBookmarkRepository
import com.github.jayteealao.crumbs.data.SyncProgress
import com.github.jayteealao.crumbs.data.SyncProgressDao
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.twitter.data.TwitterSyncFacade
import com.github.jayteealao.twitter.data.firestore.SyncCursor
import com.github.jayteealao.twitter.models.tweetEntitiesToOrderLens
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.TimeoutCancellationException
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Streaming Twitter bookmark sync. Replaces `Repository.syncFromFirestoreLocked`:
 *
 * - Survives backgrounding + process death via WorkManager.
 * - Unique-by-uid (`twitter-sync-<uid>`) so back-to-back triggers coalesce.
 * - Writes per batch (≈30 tweets) so the Paging source paints the newest
 *   bookmarks within seconds instead of after a full backlog drain.
 * - Checkpoints progress in `sync_progress` atomically with each batch insert,
 *   so a kill mid-stream resumes from the cursor on next launch.
 *
 * Cold-start backfill runs as a foreground service ("Syncing your bookmarks");
 * pull-to-refresh and sign-in re-triggers run as plain expedited work.
 */
class TwitterSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val entry = EntryPointAccessors.fromApplication(ctx, SyncEntryPoint::class.java)
        val runAsForegroundService = inputData.getBoolean(KEY_RUN_AS_FOREGROUND, false)
        val enqueuedUid = inputData.getString(KEY_UID)
        val db = entry.appDatabase()
        val syncFacade = entry.twitterSyncFacade()
        val syncProgressDao = entry.syncProgressDao()
        return runTwitterSync(
            ctx = ctx,
            syncFacade = syncFacade,
            syncProgressDao = syncProgressDao,
            deletedBookmarkRepository = entry.deletedBookmarkRepository(),
            authGateway = entry.authGateway(),
            enqueuedUid = enqueuedUid,
            runAsForegroundService = runAsForegroundService,
            runAttemptCount = runAttemptCount,
            // On API 31+ WorkManager surfaces why the worker was stopped; the
            // Android-15 dataSync 6h cap reports STOP_REASON_TIMEOUT. Used to turn
            // a timeout-driven stop into a retry (not a false "sync failed" alert).
            stopReason = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    stopReason
                } else {
                    WorkInfo.STOP_REASON_NOT_STOPPED
                }
            },
            setForegroundInfo = { setForeground(it) },
            commitBatch = { orderedBatch, progress ->
                db.withTransaction {
                    syncFacade.insertTweetEntitiesBatch(orderedBatch)
                    syncProgressDao.upsert(progress)
                }
            },
        )
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        SyncNotifications.foregroundInfo(applicationContext, batchIdx = 0, batchTotal = 0)

    companion object {
        const val KEY_RUN_AS_FOREGROUND = "run_as_foreground"
        const val KEY_UID = "uid"
        const val UNIQUE_NAME_PREFIX = "twitter-sync-"
        const val MAX_RETRY_ATTEMPTS = 5

        // Cap the local-ID dedup set loaded at sync start. Tweets beyond this
        // threshold are re-inserted with IGNORE-on-conflict (no data loss).
        // Matches the Firestore-side corpus ceiling so the two sets stay in step.
        const val MAX_LOCAL_ID_SET_SIZE = 25_000

        fun uniqueName(uid: String): String = "$UNIQUE_NAME_PREFIX$uid"

        fun buildRequest(uid: String, runAsForegroundService: Boolean): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<TwitterSyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(
                    workDataOf(
                        KEY_RUN_AS_FOREGROUND to runAsForegroundService,
                        KEY_UID to uid,
                    )
                )
                .build()
    }
}

/**
 * Extracted decision logic — exposed as a top-level suspend so Robolectric
 * tests can exercise every branch without spinning up a Hilt application.
 *
 * Cursor semantics: the persisted [SyncProgress] is read once and handed to
 * [TwitterSyncFacade.fetchMissingTweetsStream] as the resume point, so the
 * Firestore enumeration is NARROWED to the unsynced tail (server-side
 * `startAfter` on the backfill, a `retrievedAt` watermark on the incremental
 * head) instead of re-walking the whole corpus each run. The repository owns the
 * phase-aware cursor math — a head item's old `createdAt` must not move the
 * backfill cursor — and emits the cursor to persist with each batch; this worker
 * just stamps the run-scoped fields (`uid`, batch count, timestamp) and commits
 * it ATOMICALLY with the batch insert, so a kill mid-stream resumes from the last
 * committed batch.
 *
 * Concurrency: the upstream Flow is cold and sequential by construction
 * (`flowOn(Dispatchers.IO)` + a forEach in the producer). Do NOT add
 * `.buffer()` or `.flatMapMerge(...)` here — cursor advancement assumes
 * sequential per-batch commits.
 */
internal suspend fun runTwitterSync(
    ctx: Context,
    syncFacade: TwitterSyncFacade,
    syncProgressDao: SyncProgressDao,
    deletedBookmarkRepository: DeletedBookmarkRepository,
    authGateway: AuthGateway,
    enqueuedUid: String?,
    runAsForegroundService: Boolean,
    runAttemptCount: Int,
    stopReason: () -> Int = { WorkInfo.STOP_REASON_NOT_STOPPED },
    setForegroundInfo: suspend (ForegroundInfo) -> Unit,
    commitBatch: suspend (orderedBatch: List<com.github.jayteealao.twitter.models.TweetEntities>, progress: SyncProgress) -> Unit,
): androidx.work.ListenableWorker.Result {
    val uid = authGateway.currentUser.value?.uid
    if (uid.isNullOrEmpty()) {
        Timber.tag("IncrementalSync").d("skip uid=null")
        return androidx.work.ListenableWorker.Result.failure()
    }
    // Reject a job whose enqueued account no longer matches the signed-in user
    // (sign-out then sign-in to a different account while this job was queued).
    // Without this, account B's bookmarks would be fetched under account A's job
    // and written into the shared local DB. The correct per-account job is
    // enqueued separately; treat this obsolete one as a no-op success so
    // WorkManager does not retry it.
    if (!enqueuedUid.isNullOrEmpty() && enqueuedUid != uid) {
        Timber.tag("IncrementalSync").w("skip uid_mismatch enqueued=$enqueuedUid current=$uid")
        return androidx.work.ListenableWorker.Result.success()
    }

    if (runAsForegroundService) {
        runCatching { setForegroundInfo(SyncNotifications.foregroundInfo(ctx, 0, 0)) }
            .onFailure { Timber.tag("IncrementalSync").w(it, "setForegroundInfo failed (continuing as background work)") }
    }

    val priorProgress = syncProgressDao.get(uid)
    if (priorProgress != null) {
        Timber.tag("IncrementalSync")
            .i("resumed_from_cursor batchIdx=${priorProgress.totalBatchesIngested} cursorHi=${priorProgress.lastHighCursorCreatedAt} cursorLo=${priorProgress.lastLowCursorCreatedAt}")
    } else {
        Timber.tag("IncrementalSync").i("started cold uid=$uid")
    }

    var batchIdx = priorProgress?.totalBatchesIngested ?: 0
    var nextOrder = (syncFacade.getMaxOrder() ?: 1000) + 1
    // Tally of items written this run. Drives the terminal "Synced N bookmarks"
    // alert; the diff already filters to missing ids, so batch size ≈ new items
    // (IGNORE-on-conflict makes this a close approximation, fine for copy).
    var syncedCount = 0

    // The persisted cursor becomes the resume point for the narrowed enumeration: the
    // `retrievedAt` watermark bounds the incremental head and the low createdAt cursor
    // resumes the backfill tail. A null/fresh cursor seeds a full sync.
    val resumeFrom = SyncCursor(
        highCreatedAt = priorProgress?.lastHighCursorCreatedAt,
        highTweetId = priorProgress?.lastHighCursorTweetId,
        lowCreatedAt = priorProgress?.lastLowCursorCreatedAt,
        lowTweetId = priorProgress?.lastLowCursorTweetId,
        incrementalWatermarkMillis = priorProgress?.lastIncrementalRetrievedAtMs,
    )

    val result: androidx.work.ListenableWorker.Result = try {
        // Bound the local-ID set to limit heap allocation. IDs are sorted by the
        // database's default order so taking the tail keeps the most recently added
        // tweets (the ones most likely to overlap with the incoming Firestore page),
        // while tweets further back are re-inserted with IGNORE-on-conflict if they
        // reappear — correct behaviour at the cost of an extra no-op write.
        val allLocalIds = syncFacade.getAllTweetIds()
        val localIds: Set<String> = if (allLocalIds.size > TwitterSyncWorker.MAX_LOCAL_ID_SET_SIZE) {
            allLocalIds.takeLast(TwitterSyncWorker.MAX_LOCAL_ID_SET_SIZE).toHashSet()
        } else {
            allLocalIds.toHashSet()
        }
        val deletedIds = deletedBookmarkRepository.deletedIdsSnapshot(BookmarkSource.Twitter)

        syncFacade
            .fetchMissingTweetsStream(localIds, deletedIds, resumeFrom)
            .collect { emission ->
                val orderedBatch = emission.entities.map { entities ->
                    tweetEntitiesToOrderLens.modify(entities) { nextOrder++ }
                }
                // The repository computed the phase-correct cursor (the `retrievedAt`
                // watermark + the low backfill cursor); the worker only stamps the
                // run-scoped fields and commits it ATOMICALLY with the batch insert. An
                // empty-entity emission is a TERMINAL checkpoint — persist the advanced
                // cursor, ingest nothing (it records the watermark on a nothing-new run).
                val checkpointOnly = orderedBatch.isEmpty()
                val advancedProgress = SyncProgress(
                    uid = uid,
                    lastHighCursorCreatedAt = emission.cursor.highCreatedAt,
                    lastHighCursorTweetId = emission.cursor.highTweetId,
                    lastLowCursorCreatedAt = emission.cursor.lowCreatedAt,
                    lastLowCursorTweetId = emission.cursor.lowTweetId,
                    totalBatchesIngested = if (checkpointOnly) batchIdx else batchIdx + 1,
                    lastUpdatedAtMs = System.currentTimeMillis(),
                    lastIncrementalRetrievedAtMs = emission.cursor.incrementalWatermarkMillis,
                )

                commitBatch(orderedBatch, advancedProgress)
                if (checkpointOnly) return@collect

                batchIdx++
                syncedCount += orderedBatch.size
                Timber.tag("IncrementalSync")
                    .i("batch_inserted batchIdx=$batchIdx size=${orderedBatch.size} cursorLo=${emission.cursor.lowCreatedAt} watermark=${emission.cursor.incrementalWatermarkMillis}")

                if (runAsForegroundService) {
                    runCatching {
                        setForegroundInfo(SyncNotifications.foregroundInfo(ctx, batchIdx, batchIdx + 1))
                    }.onFailure { Timber.tag("IncrementalSync").w(it, "setForegroundInfo update failed") }
                }
            }

        Timber.tag("IncrementalSync").i("completed totalBatches=$batchIdx uid=$uid")
        androidx.work.ListenableWorker.Result.success()
    } catch (e: TimeoutCancellationException) {
        Timber.tag("IncrementalSync").w(e, "transient timeout attempt=$runAttemptCount")
        if (runAttemptCount >= TwitterSyncWorker.MAX_RETRY_ATTEMPTS) {
            androidx.work.ListenableWorker.Result.failure()
        } else {
            androidx.work.ListenableWorker.Result.retry()
        }
    } catch (e: CancellationException) {
        // WorkManager stops the foreground dataSync worker when the Android-15 6h cap
        // is hit, cancelling this coroutine. Per-batch commits already made progress
        // durable, so a timeout stop is a retry — NOT a real failure — and must not
        // raise the "sync failed" alert. Any other cancellation (e.g. a lost network
        // constraint) is genuine: rethrow it so structured concurrency is preserved.
        if (stopReason() == WorkInfo.STOP_REASON_TIMEOUT) {
            Timber.tag("IncrementalSync").w("stopped_by_timeout attempt=$runAttemptCount; retrying, no error alert")
            androidx.work.ListenableWorker.Result.retry()
        } else {
            throw e
        }
    } catch (e: FirebaseFirestoreException) {
        if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE && runAttemptCount < TwitterSyncWorker.MAX_RETRY_ATTEMPTS) {
            Timber.tag("IncrementalSync").w(e, "firestore unavailable, retry attempt=$runAttemptCount")
            androidx.work.ListenableWorker.Result.retry()
        } else {
            Timber.tag("IncrementalSync").e(e, "firestore_failed code=${e.code}")
            androidx.work.ListenableWorker.Result.failure()
        }
    } catch (e: IOException) {
        Timber.tag("IncrementalSync").w(e, "transient_io_failure attempt=$runAttemptCount")
        if (runAttemptCount >= TwitterSyncWorker.MAX_RETRY_ATTEMPTS) {
            androidx.work.ListenableWorker.Result.failure()
        } else {
            androidx.work.ListenableWorker.Result.retry()
        }
    } catch (e: android.database.SQLException) {
        Timber.tag("IncrementalSync").w(e, "transient_db_failure attempt=$runAttemptCount")
        if (runAttemptCount >= TwitterSyncWorker.MAX_RETRY_ATTEMPTS) {
            androidx.work.ListenableWorker.Result.failure()
        } else {
            androidx.work.ListenableWorker.Result.retry()
        }
    } catch (e: Exception) {
        Timber.tag("IncrementalSync").e(e, "unexpected_failure attempt=$runAttemptCount")
        androidx.work.ListenableWorker.Result.failure()
    }

    // Terminal alerts fire only on the foreground cold-start drain — the path that
    // raised an ongoing notification the user wasn't watching. Interactive
    // pull-to-refresh runs as background work with snackbar feedback, so a heads-up
    // "Synced N" over the live app would be noise. The timeout-driven stop returns
    // retry() above and intentionally posts neither alert.
    if (runAsForegroundService) {
        // Compare against the public Result factories (Result.Success/.Failure are
        // @RestrictTo and cannot be referenced directly). retry() matches neither.
        when {
            result == androidx.work.ListenableWorker.Result.success() && syncedCount > 0 ->
                SyncNotifications.notifyTerminalSuccess(ctx, syncedCount)
            result == androidx.work.ListenableWorker.Result.failure() ->
                SyncNotifications.notifyTerminalError(ctx)
        }
    }
    return result
}
