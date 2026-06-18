package com.github.jayteealao.crumbs.sync

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.jayteealao.crumbs.R
import com.github.jayteealao.crumbs.auth.AuthGateway
import com.github.jayteealao.crumbs.data.DeletedBookmarkRepository
import com.github.jayteealao.crumbs.data.SyncProgress
import com.github.jayteealao.crumbs.data.SyncProgressDao
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.twitter.data.TwitterSyncFacade
import com.github.jayteealao.twitter.models.tweetEntitiesToOrderLens
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.TimeoutCancellationException
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

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
        buildForegroundInfo(applicationContext, batchIdx = 0, batchTotal = 0)

    companion object {
        const val KEY_RUN_AS_FOREGROUND = "run_as_foreground"
        const val KEY_UID = "uid"
        const val UNIQUE_NAME_PREFIX = "twitter-sync-"
        const val MAX_RETRY_ATTEMPTS = 5
        const val NOTIFICATION_ID = 4242
        const val NOTIFICATION_CHANNEL_ID = "twitter_sync_progress"

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
 * Build the ongoing progress notification used when the worker is promoted to
 * a foreground service. `batchTotal == 0` (or batchIdx == 0) renders an
 * indeterminate progress bar; otherwise it's bounded. Channel registration
 * lives in `CrumbApplication.onCreate()`.
 */
internal fun buildForegroundInfo(
    ctx: Context,
    batchIdx: Int,
    batchTotal: Int,
): ForegroundInfo {
    val contentText = when {
        batchTotal > 0 -> "Batch $batchIdx of $batchTotal"
        batchIdx > 0 -> "Batch $batchIdx"
        else -> "Loading…"
    }
    val notification = NotificationCompat.Builder(ctx, TwitterSyncWorker.NOTIFICATION_CHANNEL_ID)
        .setContentTitle("Syncing your bookmarks")
        .setContentText(contentText)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .apply {
            if (batchTotal > 0) {
                setProgress(batchTotal, batchIdx, false)
            } else {
                setProgress(0, 0, true)
            }
        }
        .build()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ForegroundInfo(
            TwitterSyncWorker.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    } else {
        ForegroundInfo(TwitterSyncWorker.NOTIFICATION_ID, notification)
    }
}

/**
 * Extracted decision logic — exposed as a top-level suspend so Robolectric
 * tests can exercise every branch without spinning up a Hilt application.
 *
 * Cursor semantics: this implementation streams the full unseen tail
 * client-side (via [TwitterSyncFacade.fetchMissingTweetsStream]) instead
 * of using the persisted high-cursor as a server-side `startAfter` boundary.
 * The persisted cursor still records progress so a kill mid-stream is
 * recoverable, but the next run does a fresh "what's missing locally?" pass.
 * Server-side cursor narrowing is a follow-up — keeping the client-driven
 * filter simple here avoids the same-millisecond ordering gotcha while the
 * verify stage exercises the new pipeline end-to-end.
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
        runCatching { setForegroundInfo(buildForegroundInfo(ctx, 0, 0)) }
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
    var workingHighCreatedAt = priorProgress?.lastHighCursorCreatedAt
    var workingHighTweetId = priorProgress?.lastHighCursorTweetId
    var workingLowCreatedAt = priorProgress?.lastLowCursorCreatedAt
    var workingLowTweetId = priorProgress?.lastLowCursorTweetId
    var nextOrder = (syncFacade.getMaxOrder() ?: 1000) + 1

    return try {
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
            .fetchMissingTweetsStream(localIds, deletedIds)
            .collect { batch ->
                if (batch.isEmpty()) return@collect

                val orderedBatch = batch.map { entities ->
                    tweetEntitiesToOrderLens.modify(entities) { nextOrder++ }
                }

                val batchHigh = orderedBatch.firstOrNull()
                val batchLow = orderedBatch.lastOrNull()
                val batchHighCreatedAt = batchHigh?.tweetEntity?.createdAt
                val batchHighTweetId = batchHigh?.tweetEntity?.id
                val batchLowCreatedAt = batchLow?.tweetEntity?.createdAt
                val batchLowTweetId = batchLow?.tweetEntity?.id

                if (workingHighCreatedAt == null || (batchHighCreatedAt != null && batchHighCreatedAt > workingHighCreatedAt!!)) {
                    workingHighCreatedAt = batchHighCreatedAt
                    workingHighTweetId = batchHighTweetId
                }
                if (workingLowCreatedAt == null || (batchLowCreatedAt != null && batchLowCreatedAt < workingLowCreatedAt!!)) {
                    workingLowCreatedAt = batchLowCreatedAt
                    workingLowTweetId = batchLowTweetId
                }

                val advancedProgress = SyncProgress(
                    uid = uid,
                    lastHighCursorCreatedAt = workingHighCreatedAt,
                    lastHighCursorTweetId = workingHighTweetId,
                    lastLowCursorCreatedAt = workingLowCreatedAt,
                    lastLowCursorTweetId = workingLowTweetId,
                    totalBatchesIngested = batchIdx + 1,
                    lastUpdatedAtMs = System.currentTimeMillis(),
                )

                commitBatch(orderedBatch, advancedProgress)

                batchIdx++
                Timber.tag("IncrementalSync")
                    .i("batch_inserted batchIdx=$batchIdx size=${orderedBatch.size} cursorHi=$workingHighCreatedAt cursorLo=$workingLowCreatedAt")

                if (runAsForegroundService) {
                    runCatching {
                        setForegroundInfo(buildForegroundInfo(ctx, batchIdx, batchIdx + 1))
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
}
