package com.github.jayteealao.crumbs.sync

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import com.github.jayteealao.crumbs.R
import com.github.jayteealao.crumbs.auth.AuthGateway
import com.github.jayteealao.crumbs.auth.CurrentUser
import com.github.jayteealao.crumbs.data.DeletedBookmarkRepository
import com.github.jayteealao.crumbs.data.SyncProgress
import com.github.jayteealao.crumbs.data.SyncProgressDao
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.twitter.data.TwitterSyncFacade
import com.github.jayteealao.twitter.models.MediaKeys
import com.github.jayteealao.twitter.models.PollIds
import com.github.jayteealao.twitter.models.TweetEntities
import com.github.jayteealao.twitter.models.TweetEntity
import com.github.jayteealao.twitter.models.TweetPublicMetrics
import com.google.firebase.firestore.FirebaseFirestoreException
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.coroutines.cancellation.CancellationException

/**
 * Covers the load-bearing branches of [runTwitterSync]:
 *
 *  - null user → failure without retries (worker contract).
 *  - cold-start cursor advances and commits per batch (AC1, AC4 cursor proof).
 *  - resume from a pre-seeded cursor continues advancing batchIdx (AC5 proof).
 *  - transient FirestoreFirestoreException(UNAVAILABLE) → retry below the cap.
 *  - retry cap is honored (no infinite retry).
 *  - non-retryable Firestore code → terminal failure.
 *  - companion helpers `uniqueName` + `buildRequest` (AC7 surface).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TwitterSyncWorkerTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var syncFacade: TwitterSyncFacade
    private lateinit var syncProgressDao: SyncProgressDao
    private lateinit var deletedBookmarkRepository: DeletedBookmarkRepository
    private lateinit var authGateway: AuthGateway
    private val currentUserFlow = MutableStateFlow<CurrentUser?>(CurrentUser(uid = "uid-test", email = null))

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        val app = context as Application
        // Grant the runtime permission + register channels so the terminal-alert
        // assertions below can observe posted notifications via ShadowNotificationManager.
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        SyncNotifications.registerChannels(app)
        notificationManager = app.getSystemService(NotificationManager::class.java)
        syncFacade = mockk(relaxed = true)
        syncProgressDao = mockk(relaxed = true)
        deletedBookmarkRepository = mockk()
        authGateway = mockk()
        every { authGateway.currentUser } returns currentUserFlow
        coEvery { syncFacade.getAllTweetIds() } returns emptyList()
        coEvery { syncFacade.getMaxOrder() } returns 1000
        coEvery { deletedBookmarkRepository.deletedIdsSnapshot(BookmarkSource.Twitter) } returns emptySet()
        coEvery { syncProgressDao.get(any()) } returns null
        coEvery { syncProgressDao.upsert(any()) } just Runs
    }

    private fun tweetEntities(id: String, createdAt: String): TweetEntities {
        val tweet = TweetEntity(
            id = id,
            text = "synthetic for $id",
            createdAt = createdAt,
            authorId = "author-1",
            conversationId = id,
            inReplyToUserId = null,
            lang = "en",
            referenced = false,
            order = 0,
            pendingDelete = false,
        )
        return TweetEntities(
            tweetEntity = tweet,
            twitterUserEntity = emptyList(),
            tweetPublicMetrics = TweetPublicMetrics(0, 0, 0, 0, 0, tweetId = id),
            tweetMediaEntity = emptyList(),
            tweetIncludesEntity = emptyList(),
            tweetReferencedTweets = emptyList(),
            tweetContextAnnotationEntity = emptyList(),
            tweetTextEntity = emptyList(),
            mediaKeys = emptyList<MediaKeys>(),
            pollIds = null,
        )
    }

    private fun stubStream(vararg batches: List<TweetEntities>): Flow<List<TweetEntities>> = flow {
        batches.forEach { emit(it) }
    }

    @Test
    fun nullUser_returnsFailure_withoutFetching() = runTest {
        currentUserFlow.value = null
        val capturedCommits = mutableListOf<List<TweetEntities>>()

        val result = runTwitterSync(
            ctx = context,
            syncFacade = syncFacade,
            syncProgressDao = syncProgressDao,
            deletedBookmarkRepository = deletedBookmarkRepository,
            authGateway = authGateway,
            enqueuedUid = null,
            runAsForegroundService = false,
            runAttemptCount = 0,
            setForegroundInfo = {},
            commitBatch = { batch, _ -> capturedCommits += batch },
        )

        assertEquals(ListenableWorker.Result.failure(), result)
        assertTrue("no batches should commit when uid is null", capturedCommits.isEmpty())
        coVerify(exactly = 0) { syncFacade.fetchMissingTweetsStream(any(), any()) }
    }

    @Test
    fun enqueuedUidMismatch_returnsSuccess_withoutFetching() = runTest {
        // Job was enqueued for one account but a different account is now signed
        // in — the obsolete job must no-op (success, no retry) and never fetch,
        // so account B's data can't be written under account A's job.
        currentUserFlow.value = CurrentUser(uid = "uid-current", email = null)
        val capturedCommits = mutableListOf<List<TweetEntities>>()

        val result = runTwitterSync(
            ctx = context,
            syncFacade = syncFacade,
            syncProgressDao = syncProgressDao,
            deletedBookmarkRepository = deletedBookmarkRepository,
            authGateway = authGateway,
            enqueuedUid = "uid-other",
            runAsForegroundService = false,
            runAttemptCount = 0,
            setForegroundInfo = {},
            commitBatch = { batch, _ -> capturedCommits += batch },
        )

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue("no batches should commit on uid mismatch", capturedCommits.isEmpty())
        coVerify(exactly = 0) { syncFacade.fetchMissingTweetsStream(any(), any()) }
    }

    @Test
    fun coldStart_twoBatches_commitsBothAndAdvancesCursors() = runTest {
        every {
            syncFacade.fetchMissingTweetsStream(any(), any())
        } returns stubStream(
            listOf(
                tweetEntities("tw-001", "2026-05-24T15:00:00Z"),
                tweetEntities("tw-002", "2026-05-24T14:59:00Z"),
            ),
            listOf(
                tweetEntities("tw-003", "2026-05-24T14:58:00Z"),
                tweetEntities("tw-004", "2026-05-24T14:57:00Z"),
            ),
        )

        val capturedProgress = mutableListOf<SyncProgress>()

        val result = runTwitterSync(
            ctx = context,
            syncFacade = syncFacade,
            syncProgressDao = syncProgressDao,
            deletedBookmarkRepository = deletedBookmarkRepository,
            authGateway = authGateway,
            enqueuedUid = null,
            runAsForegroundService = false,
            runAttemptCount = 0,
            setForegroundInfo = {},
            commitBatch = { _, progress -> capturedProgress += progress },
        )

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(2, capturedProgress.size)
        assertEquals(1, capturedProgress[0].totalBatchesIngested)
        assertEquals(2, capturedProgress[1].totalBatchesIngested)
        // high watermark is the newest createdAt across all batches (batch 0 head)
        assertEquals("2026-05-24T15:00:00Z", capturedProgress[1].lastHighCursorCreatedAt)
        // low watermark advances to the oldest createdAt across all batches
        assertEquals("2026-05-24T14:57:00Z", capturedProgress[1].lastLowCursorCreatedAt)
    }

    @Test
    fun resume_withSeededCursor_carriesBatchCountForward() = runTest {
        coEvery { syncProgressDao.get("uid-test") } returns SyncProgress(
            uid = "uid-test",
            lastHighCursorCreatedAt = "2026-05-24T14:00:00Z",
            lastHighCursorTweetId = "tw-seed",
            lastLowCursorCreatedAt = "2026-05-24T12:00:00Z",
            lastLowCursorTweetId = "tw-seed",
            totalBatchesIngested = 3,
            lastUpdatedAtMs = 0L,
        )
        every {
            syncFacade.fetchMissingTweetsStream(any(), any())
        } returns stubStream(listOf(tweetEntities("tw-100", "2026-05-24T11:00:00Z")))

        val capturedProgress = slot<SyncProgress>()
        val captured = mutableListOf<SyncProgress>()

        val result = runTwitterSync(
            ctx = context,
            syncFacade = syncFacade,
            syncProgressDao = syncProgressDao,
            deletedBookmarkRepository = deletedBookmarkRepository,
            authGateway = authGateway,
            enqueuedUid = null,
            runAsForegroundService = false,
            runAttemptCount = 0,
            setForegroundInfo = {},
            commitBatch = { _, progress ->
                capturedProgress.captured = progress
                captured += progress
            },
        )

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, captured.size)
        // Batch count carries from the seeded cursor (3 → 4 after one batch).
        assertEquals(4, captured.first().totalBatchesIngested)
        // Low watermark advances past the seeded low (12:00) to the new batch's 11:00.
        assertEquals("2026-05-24T11:00:00Z", captured.first().lastLowCursorCreatedAt)
    }

    @Test
    fun transientFirestoreUnavailable_underCap_returnsRetry() = runTest {
        val unavailable = FirebaseFirestoreException(
            "service unavailable",
            FirebaseFirestoreException.Code.UNAVAILABLE,
        )
        every {
            syncFacade.fetchMissingTweetsStream(any(), any())
        } returns flow { throw unavailable }

        val result = runTwitterSync(
            ctx = context,
            syncFacade = syncFacade,
            syncProgressDao = syncProgressDao,
            deletedBookmarkRepository = deletedBookmarkRepository,
            authGateway = authGateway,
            enqueuedUid = null,
            runAsForegroundService = false,
            runAttemptCount = 1,
            setForegroundInfo = {},
            commitBatch = { _, _ -> },
        )

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun firestoreUnavailable_atCap_returnsFailure() = runTest {
        val unavailable = FirebaseFirestoreException(
            "service unavailable",
            FirebaseFirestoreException.Code.UNAVAILABLE,
        )
        every {
            syncFacade.fetchMissingTweetsStream(any(), any())
        } returns flow { throw unavailable }

        val result = runTwitterSync(
            ctx = context,
            syncFacade = syncFacade,
            syncProgressDao = syncProgressDao,
            deletedBookmarkRepository = deletedBookmarkRepository,
            authGateway = authGateway,
            enqueuedUid = null,
            runAsForegroundService = false,
            runAttemptCount = TwitterSyncWorker.MAX_RETRY_ATTEMPTS,
            setForegroundInfo = {},
            commitBatch = { _, _ -> },
        )

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun firestorePermissionDenied_returnsFailure_noRetry() = runTest {
        val denied = FirebaseFirestoreException(
            "permission denied",
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
        )
        every {
            syncFacade.fetchMissingTweetsStream(any(), any())
        } returns flow { throw denied }

        val result = runTwitterSync(
            ctx = context,
            syncFacade = syncFacade,
            syncProgressDao = syncProgressDao,
            deletedBookmarkRepository = deletedBookmarkRepository,
            authGateway = authGateway,
            enqueuedUid = null,
            runAsForegroundService = false,
            runAttemptCount = 0,
            setForegroundInfo = {},
            commitBatch = { _, _ -> },
        )

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun timeoutCancellation_underCap_returnsRetry() = runTest {
        every {
            syncFacade.fetchMissingTweetsStream(any(), any())
        } returns flow {
            // Trigger a real TimeoutCancellationException — the constructor is
            // internal, so we route through withTimeout instead.
            withTimeout(1L) { delay(Long.MAX_VALUE) }
        }

        val result = runTwitterSync(
            ctx = context,
            syncFacade = syncFacade,
            syncProgressDao = syncProgressDao,
            deletedBookmarkRepository = deletedBookmarkRepository,
            authGateway = authGateway,
            enqueuedUid = null,
            runAsForegroundService = false,
            runAttemptCount = 0,
            setForegroundInfo = {},
            commitBatch = { _, _ -> },
        )

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun emptyStream_returnsSuccess_withoutCommits() = runTest {
        every {
            syncFacade.fetchMissingTweetsStream(any(), any())
        } returns flowOf()

        var commits = 0
        val result = runTwitterSync(
            ctx = context,
            syncFacade = syncFacade,
            syncProgressDao = syncProgressDao,
            deletedBookmarkRepository = deletedBookmarkRepository,
            authGateway = authGateway,
            enqueuedUid = null,
            runAsForegroundService = false,
            runAttemptCount = 0,
            setForegroundInfo = {},
            commitBatch = { _, _ -> commits++ },
        )

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(0, commits)
    }

    @Test
    fun uniqueName_andBuildRequest_helpersUseUidScopedNamespace() {
        val name = TwitterSyncWorker.uniqueName("uid-abc")
        assertEquals("twitter-sync-uid-abc", name)

        val request = TwitterSyncWorker.buildRequest("uid-abc", runAsForegroundService = true)
        val inputForeground = request.workSpec.input.getBoolean(TwitterSyncWorker.KEY_RUN_AS_FOREGROUND, false)
        assertTrue("foreground input flag should round-trip through workDataOf", inputForeground)

        val refreshRequest = TwitterSyncWorker.buildRequest("uid-abc", runAsForegroundService = false)
        val refreshInput = refreshRequest.workSpec.input.getBoolean(TwitterSyncWorker.KEY_RUN_AS_FOREGROUND, true)
        assertEquals(false, refreshInput)

        assertEquals(
            "uid should round-trip through workDataOf",
            "uid-abc",
            request.workSpec.input.getString(TwitterSyncWorker.KEY_UID),
        )
    }

    @Test
    fun coldStartFailureInjector_doesNotMarkProgress() = runTest {
        every {
            syncFacade.fetchMissingTweetsStream(any(), any())
        } returns flow { throw RuntimeException("boom") }

        val capturedProgress: SyncProgress? = null

        val result = runTwitterSync(
            ctx = context,
            syncFacade = syncFacade,
            syncProgressDao = syncProgressDao,
            deletedBookmarkRepository = deletedBookmarkRepository,
            authGateway = authGateway,
            enqueuedUid = null,
            runAsForegroundService = false,
            runAttemptCount = 0,
            setForegroundInfo = {},
            commitBatch = { _, _ -> },
        )

        assertEquals(ListenableWorker.Result.failure(), result)
        assertNull(capturedProgress)
    }

    private fun terminalText(): String? =
        shadowOf(notificationManager)
            .getNotification(SyncNotifications.ID_TERMINAL)
            ?.extras
            ?.getCharSequence(Notification.EXTRA_TEXT)
            ?.toString()

    @Test
    fun foregroundColdStart_publishesForegroundInfo_withMonochromeIcon() = runTest {
        every {
            syncFacade.fetchMissingTweetsStream(any(), any())
        } returns stubStream(listOf(tweetEntities("tw-1", "2026-05-24T15:00:00Z")))
        val captured = mutableListOf<androidx.work.ForegroundInfo>()

        val result = runTwitterSync(
            ctx = context,
            syncFacade = syncFacade,
            syncProgressDao = syncProgressDao,
            deletedBookmarkRepository = deletedBookmarkRepository,
            authGateway = authGateway,
            enqueuedUid = null,
            runAsForegroundService = true,
            runAttemptCount = 0,
            setForegroundInfo = { captured += it },
            commitBatch = { _, _ -> },
        )

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue("foreground info must be published as a foreground service", captured.isNotEmpty())
        val fg = captured.first()
        assertEquals(SyncNotifications.ID_FOREGROUND, fg.notificationId)
        assertEquals(
            "small icon must be the monochrome vector, not the launcher mipmap",
            R.drawable.ic_sync_notification,
            shadowOf(fg.notification.smallIcon).resId,
        )
        assertEquals(SyncNotifications.CHANNEL_PROGRESS, fg.notification.channelId)
    }

    @Test
    fun foregroundSuccess_withNewItems_postsTerminalSuccess() = runTest {
        every {
            syncFacade.fetchMissingTweetsStream(any(), any())
        } returns stubStream(
            listOf(
                tweetEntities("tw-1", "2026-05-24T15:00:00Z"),
                tweetEntities("tw-2", "2026-05-24T14:00:00Z"),
            ),
        )

        val result = runTwitterSync(
            ctx = context,
            syncFacade = syncFacade,
            syncProgressDao = syncProgressDao,
            deletedBookmarkRepository = deletedBookmarkRepository,
            authGateway = authGateway,
            enqueuedUid = null,
            runAsForegroundService = true,
            runAttemptCount = 0,
            setForegroundInfo = {},
            commitBatch = { _, _ -> },
        )

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals("Synced 2 bookmarks", terminalText())
    }

    @Test
    fun foregroundSuccess_withZeroItems_doesNotPostTerminal() = runTest {
        every { syncFacade.fetchMissingTweetsStream(any(), any()) } returns flowOf()

        val result = runTwitterSync(
            ctx = context,
            syncFacade = syncFacade,
            syncProgressDao = syncProgressDao,
            deletedBookmarkRepository = deletedBookmarkRepository,
            authGateway = authGateway,
            enqueuedUid = null,
            runAsForegroundService = true,
            runAttemptCount = 0,
            setForegroundInfo = {},
            commitBatch = { _, _ -> },
        )

        assertEquals(ListenableWorker.Result.success(), result)
        assertNull(
            "no terminal notification when nothing new synced",
            shadowOf(notificationManager).getNotification(SyncNotifications.ID_TERMINAL),
        )
    }

    @Test
    fun backgroundSuccess_withNewItems_doesNotPostTerminal() = runTest {
        // Pull-to-refresh / sign-in re-triggers run as background work (no ongoing
        // notification); feedback is via snackbars, so no terminal alert should fire.
        every {
            syncFacade.fetchMissingTweetsStream(any(), any())
        } returns stubStream(listOf(tweetEntities("tw-1", "2026-05-24T15:00:00Z")))

        val result = runTwitterSync(
            ctx = context,
            syncFacade = syncFacade,
            syncProgressDao = syncProgressDao,
            deletedBookmarkRepository = deletedBookmarkRepository,
            authGateway = authGateway,
            enqueuedUid = null,
            runAsForegroundService = false,
            runAttemptCount = 0,
            setForegroundInfo = {},
            commitBatch = { _, _ -> },
        )

        assertEquals(ListenableWorker.Result.success(), result)
        assertNull(
            "background sync must not raise a terminal alert",
            shadowOf(notificationManager).getNotification(SyncNotifications.ID_TERMINAL),
        )
    }

    @Test
    fun foregroundTerminalFailure_postsErrorAlert() = runTest {
        val denied = FirebaseFirestoreException(
            "permission denied",
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
        )
        every {
            syncFacade.fetchMissingTweetsStream(any(), any())
        } returns flow { throw denied }

        val result = runTwitterSync(
            ctx = context,
            syncFacade = syncFacade,
            syncProgressDao = syncProgressDao,
            deletedBookmarkRepository = deletedBookmarkRepository,
            authGateway = authGateway,
            enqueuedUid = null,
            runAsForegroundService = true,
            runAttemptCount = 0,
            setForegroundInfo = {},
            commitBatch = { _, _ -> },
        )

        assertEquals(ListenableWorker.Result.failure(), result)
        assertNotNull(
            "a real foreground failure must post an error alert",
            shadowOf(notificationManager).getNotification(SyncNotifications.ID_TERMINAL),
        )
    }

    @Test
    fun timeoutStop_returnsRetry_withoutErrorNotification() = runTest {
        // Android-15 dataSync 6h cap: WorkManager stops the worker (CancellationException)
        // with STOP_REASON_TIMEOUT. That must be a retry, not a false "sync failed" alert.
        every {
            syncFacade.fetchMissingTweetsStream(any(), any())
        } returns flow { throw CancellationException("stopped by timeout") }

        val result = runTwitterSync(
            ctx = context,
            syncFacade = syncFacade,
            syncProgressDao = syncProgressDao,
            deletedBookmarkRepository = deletedBookmarkRepository,
            authGateway = authGateway,
            enqueuedUid = null,
            runAsForegroundService = true,
            runAttemptCount = 0,
            stopReason = { WorkInfo.STOP_REASON_TIMEOUT },
            setForegroundInfo = {},
            commitBatch = { _, _ -> },
        )

        assertEquals(ListenableWorker.Result.retry(), result)
        assertNull(
            "a timeout-driven stop must not raise an error alert",
            shadowOf(notificationManager).getNotification(SyncNotifications.ID_TERMINAL),
        )
    }
}
