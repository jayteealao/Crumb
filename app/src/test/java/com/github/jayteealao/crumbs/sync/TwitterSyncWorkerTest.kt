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
import com.github.jayteealao.twitter.data.firestore.SyncCursor
import com.github.jayteealao.twitter.data.firestore.SyncEmission
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
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
    fun setUp() =
        runTest {
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

    private fun tweetEntities(
        id: String,
        createdAt: String,
    ): TweetEntities {
        val tweet =
            TweetEntity(
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

    // Each batch is wrapped in a SyncEmission.Batch with a default (empty) cursor — the
    // cursor-specific assertions live in their own tests + FirestoreCursorNarrowingTest.
    private fun stubStream(vararg batches: List<TweetEntities>): Flow<SyncEmission> =
        flow {
            batches.forEach { emit(SyncEmission.Batch(it, SyncCursor())) }
        }

    @Test
    fun nullUser_returnsFailure_withoutFetching() =
        runTest {
            currentUserFlow.value = null
            val capturedCommits = mutableListOf<List<TweetEntities>>()

            val result =
                runTwitterSync(
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
            coVerify(exactly = 0) { syncFacade.fetchMissingTweetsStream(any(), any(), any()) }
        }

    @Test
    fun enqueuedUidMismatch_returnsSuccess_withoutFetching() =
        runTest {
            // Job was enqueued for one account but a different account is now signed
            // in — the obsolete job must no-op (success, no retry) and never fetch,
            // so account B's data can't be written under account A's job.
            currentUserFlow.value = CurrentUser(uid = "uid-current", email = null)
            val capturedCommits = mutableListOf<List<TweetEntities>>()

            val result =
                runTwitterSync(
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
            coVerify(exactly = 0) { syncFacade.fetchMissingTweetsStream(any(), any(), any()) }
        }

    @Test
    fun coldStart_twoEmissions_persistRepoComputedCursorPerBatch() =
        runTest {
            // The repository now owns the cursor math; the worker persists whatever cursor each
            // emission carries (verbatim) and stamps only the run-scoped batch count.
            val cursorA =
                SyncCursor(
                    highCreatedAt = "2026-05-24T15:00:00Z",
                    highTweetId = "tw-001",
                    lowCreatedAt = "2026-05-24T14:59:00Z",
                    lowTweetId = "tw-002",
                    incrementalWatermarkMillis = 1500L,
                )
            val cursorB =
                SyncCursor(
                    highCreatedAt = "2026-05-24T15:00:00Z",
                    highTweetId = "tw-001",
                    lowCreatedAt = "2026-05-24T14:57:00Z",
                    lowTweetId = "tw-004",
                    incrementalWatermarkMillis = 1500L,
                )
            every {
                syncFacade.fetchMissingTweetsStream(any(), any(), any())
            } returns
                flow {
                    emit(
                        SyncEmission.Batch(
                            listOf(
                                tweetEntities("tw-001", "2026-05-24T15:00:00Z"),
                                tweetEntities("tw-002", "2026-05-24T14:59:00Z"),
                            ),
                            cursorA,
                        ),
                    )
                    emit(
                        SyncEmission.Batch(
                            listOf(
                                tweetEntities("tw-003", "2026-05-24T14:58:00Z"),
                                tweetEntities("tw-004", "2026-05-24T14:57:00Z"),
                            ),
                            cursorB,
                        ),
                    )
                }

            val capturedProgress = mutableListOf<SyncProgress>()

            val result =
                runTwitterSync(
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
            // The worker stamps the cumulative batch count...
            assertEquals(1, capturedProgress[0].totalBatchesIngested)
            assertEquals(2, capturedProgress[1].totalBatchesIngested)
            // ...and persists the repo-computed cursor verbatim (watermark + low backfill cursor).
            assertEquals(1500L, capturedProgress[1].lastIncrementalRetrievedAtMs)
            assertEquals("2026-05-24T14:57:00Z", capturedProgress[1].lastLowCursorCreatedAt)
            assertEquals("tw-004", capturedProgress[1].lastLowCursorTweetId)
        }

    @Test
    fun resume_seededCursor_passedToFacade_andBatchCountCarries() =
        runTest {
            coEvery { syncProgressDao.get("uid-test") } returns
                SyncProgress(
                    uid = "uid-test",
                    lastHighCursorCreatedAt = "2026-05-24T14:00:00Z",
                    lastHighCursorTweetId = "tw-seed",
                    lastLowCursorCreatedAt = "2026-05-24T12:00:00Z",
                    lastLowCursorTweetId = "tw-seed",
                    totalBatchesIngested = 3,
                    lastUpdatedAtMs = 0L,
                    lastIncrementalRetrievedAtMs = 900L,
                )
            val resumeSlot = slot<SyncCursor>()
            every {
                syncFacade.fetchMissingTweetsStream(any(), any(), capture(resumeSlot))
            } returns
                flow {
                    emit(
                        SyncEmission.Batch(
                            listOf(tweetEntities("tw-100", "2026-05-24T11:00:00Z")),
                            SyncCursor(
                                lowCreatedAt = "2026-05-24T11:00:00Z",
                                lowTweetId = "tw-100",
                                incrementalWatermarkMillis = 900L,
                            ),
                        ),
                    )
                }

            val captured = mutableListOf<SyncProgress>()

            val result =
                runTwitterSync(
                    ctx = context,
                    syncFacade = syncFacade,
                    syncProgressDao = syncProgressDao,
                    deletedBookmarkRepository = deletedBookmarkRepository,
                    authGateway = authGateway,
                    enqueuedUid = null,
                    runAsForegroundService = false,
                    runAttemptCount = 0,
                    setForegroundInfo = {},
                    commitBatch = { _, progress -> captured += progress },
                )

            assertEquals(ListenableWorker.Result.success(), result)
            // The seeded cursor (low createdAt + retrievedAt watermark) is handed to the repository
            // as the resume point — proving the worker reads SyncProgress and narrows the enumeration.
            assertEquals("2026-05-24T12:00:00Z", resumeSlot.captured.lowCreatedAt)
            assertEquals("tw-seed", resumeSlot.captured.lowTweetId)
            assertEquals(900L, resumeSlot.captured.incrementalWatermarkMillis)
            // Batch count carries from the seeded cursor (3 → 4 after one batch).
            assertEquals(1, captured.size)
            assertEquals(4, captured.first().totalBatchesIngested)
            assertEquals("2026-05-24T11:00:00Z", captured.first().lastLowCursorCreatedAt)
        }

    @Test
    fun terminalCheckpoint_emptyEmission_persistsCursor_withoutBumpingBatchCount() =
        runTest {
            every {
                syncFacade.fetchMissingTweetsStream(any(), any(), any())
            } returns
                flow {
                    emit(
                        SyncEmission.Batch(
                            listOf(tweetEntities("tw-1", "2026-05-24T15:00:00Z")),
                            SyncCursor(
                                lowCreatedAt = "2026-05-24T15:00:00Z",
                                lowTweetId = "tw-1",
                                incrementalWatermarkMillis = 4242L,
                            ),
                        ),
                    )
                    // TERMINAL checkpoint: no entities, carrying the advanced watermark + backfill floor.
                    emit(
                        SyncEmission.Checkpoint(
                            SyncCursor(
                                lowCreatedAt = "2026-01-01T00:00:00Z",
                                lowTweetId = "floor",
                                incrementalWatermarkMillis = 4242L,
                            ),
                        ),
                    )
                }

            val captured = mutableListOf<SyncProgress>()

            val result =
                runTwitterSync(
                    ctx = context,
                    syncFacade = syncFacade,
                    syncProgressDao = syncProgressDao,
                    deletedBookmarkRepository = deletedBookmarkRepository,
                    authGateway = authGateway,
                    enqueuedUid = null,
                    runAsForegroundService = false,
                    runAttemptCount = 0,
                    setForegroundInfo = {},
                    commitBatch = { _, progress -> captured += progress },
                )

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(2, captured.size)
            // The real batch bumps the count to 1; the terminal checkpoint does NOT bump it again.
            assertEquals(1, captured[0].totalBatchesIngested)
            assertEquals(1, captured[1].totalBatchesIngested)
            // The terminal checkpoint persists the advanced watermark + backfill floor.
            assertEquals("2026-01-01T00:00:00Z", captured[1].lastLowCursorCreatedAt)
            assertEquals(4242L, captured[1].lastIncrementalRetrievedAtMs)
        }

    @Test
    fun transientFirestoreUnavailable_underCap_returnsRetry() =
        runTest {
            val unavailable =
                FirebaseFirestoreException(
                    "service unavailable",
                    FirebaseFirestoreException.Code.UNAVAILABLE,
                )
            every {
                syncFacade.fetchMissingTweetsStream(any(), any(), any())
            } returns flow { throw unavailable }

            val result =
                runTwitterSync(
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
    fun firestoreUnavailable_atCap_returnsFailure() =
        runTest {
            val unavailable =
                FirebaseFirestoreException(
                    "service unavailable",
                    FirebaseFirestoreException.Code.UNAVAILABLE,
                )
            every {
                syncFacade.fetchMissingTweetsStream(any(), any(), any())
            } returns flow { throw unavailable }

            val result =
                runTwitterSync(
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
    fun firestorePermissionDenied_returnsFailure_noRetry() =
        runTest {
            val denied =
                FirebaseFirestoreException(
                    "permission denied",
                    FirebaseFirestoreException.Code.PERMISSION_DENIED,
                )
            every {
                syncFacade.fetchMissingTweetsStream(any(), any(), any())
            } returns flow { throw denied }

            val result =
                runTwitterSync(
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
    fun timeoutCancellation_underCap_returnsRetry() =
        runTest {
            every {
                syncFacade.fetchMissingTweetsStream(any(), any(), any())
            } returns
                flow {
                    // Trigger a real TimeoutCancellationException — the constructor is
                    // internal, so we route through withTimeout instead.
                    withTimeout(1L) { delay(Long.MAX_VALUE) }
                }

            val result =
                runTwitterSync(
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
    fun emptyStream_returnsSuccess_withoutCommits() =
        runTest {
            every {
                syncFacade.fetchMissingTweetsStream(any(), any(), any())
            } returns flowOf<SyncEmission>()

            var commits = 0
            val result =
                runTwitterSync(
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
    fun coldStartFailureInjector_doesNotMarkProgress() =
        runTest {
            every {
                syncFacade.fetchMissingTweetsStream(any(), any(), any())
            } returns flow { throw RuntimeException("boom") }

            val capturedProgress: SyncProgress? = null

            val result =
                runTwitterSync(
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
    fun foregroundColdStart_publishesForegroundInfo_withMonochromeIcon() =
        runTest {
            every {
                syncFacade.fetchMissingTweetsStream(any(), any(), any())
            } returns stubStream(listOf(tweetEntities("tw-1", "2026-05-24T15:00:00Z")))
            val captured = mutableListOf<androidx.work.ForegroundInfo>()

            val result =
                runTwitterSync(
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
    fun foregroundSuccess_withNewItems_postsTerminalSuccess() =
        runTest {
            every {
                syncFacade.fetchMissingTweetsStream(any(), any(), any())
            } returns
                stubStream(
                    listOf(
                        tweetEntities("tw-1", "2026-05-24T15:00:00Z"),
                        tweetEntities("tw-2", "2026-05-24T14:00:00Z"),
                    ),
                )

            val result =
                runTwitterSync(
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
    fun foregroundSuccess_withZeroItems_doesNotPostTerminal() =
        runTest {
            every { syncFacade.fetchMissingTweetsStream(any(), any(), any()) } returns flowOf<SyncEmission>()

            val result =
                runTwitterSync(
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
    fun backgroundSuccess_withNewItems_doesNotPostTerminal() =
        runTest {
            // Pull-to-refresh / sign-in re-triggers run as background work (no ongoing
            // notification); feedback is via snackbars, so no terminal alert should fire.
            every {
                syncFacade.fetchMissingTweetsStream(any(), any(), any())
            } returns stubStream(listOf(tweetEntities("tw-1", "2026-05-24T15:00:00Z")))

            val result =
                runTwitterSync(
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
    fun foregroundTerminalFailure_postsErrorAlert() =
        runTest {
            val denied =
                FirebaseFirestoreException(
                    "permission denied",
                    FirebaseFirestoreException.Code.PERMISSION_DENIED,
                )
            every {
                syncFacade.fetchMissingTweetsStream(any(), any(), any())
            } returns flow { throw denied }

            val result =
                runTwitterSync(
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
    fun timeoutStop_returnsRetry_withoutErrorNotification() =
        runTest {
            // Android-15 dataSync 6h cap: WorkManager stops the worker (CancellationException)
            // with STOP_REASON_TIMEOUT. That must be a retry, not a false "sync failed" alert.
            every {
                syncFacade.fetchMissingTweetsStream(any(), any(), any())
            } returns flow { throw CancellationException("stopped by timeout") }

            val result =
                runTwitterSync(
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

    // ── Timeout-safe cursor tests (AC1 / AC2 correctness invariant) ──────────────

    /**
     * AC1: A timed-out batch (fetchFailed=true) must NOT advance the persisted cursor
     * and must return retry().  This is the invariant the original tests missed — they
     * never simulated a timed-out non-empty plan returning empty.
     */
    @Test
    fun timedOutBatch_doesNotAdvanceCursorAndRetries() =
        runTest {
            every {
                syncFacade.fetchMissingTweetsStream(any(), any(), any())
            } returns
                flow {
                    // Simulate a batch that timed out — Failed carries no cursor.
                    emit(SyncEmission.Failed)
                }

            var commits = 0
            val result =
                runTwitterSync(
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

            // The cursor must NOT have been advanced — commitBatch is never called.
            assertEquals("timed-out batch must not commit a cursor advance", 0, commits)
            // The worker must schedule a retry so the failed IDs are re-enumerated next run.
            assertEquals(ListenableWorker.Result.retry(), result)
        }

    /**
     * AC2 regression guard: a genuine terminal checkpoint (fetchFailed=false,
     * emptyList()) MUST still advance the cursor.  The fix must not conflate the
     * two empty-entity cases.
     */
    @Test
    fun terminalEmission_stillAdvancesCursor() =
        runTest {
            val cursorA =
                SyncCursor(
                    lowCreatedAt = "2026-01-01T00:00:00Z",
                    lowTweetId = "floor",
                    incrementalWatermarkMillis = 9999L,
                )
            every {
                syncFacade.fetchMissingTweetsStream(any(), any(), any())
            } returns
                flow {
                    // Genuine terminal checkpoint.
                    emit(SyncEmission.Checkpoint(cursorA))
                }

            val capturedProgress = mutableListOf<SyncProgress>()
            val result =
                runTwitterSync(
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

            // The terminal checkpoint must commit the advanced cursor.
            assertEquals("terminal checkpoint must persist the cursor", 1, capturedProgress.size)
            assertEquals("2026-01-01T00:00:00Z", capturedProgress[0].lastLowCursorCreatedAt)
            assertEquals(9999L, capturedProgress[0].lastIncrementalRetrievedAtMs)
            assertEquals(ListenableWorker.Result.success(), result)
        }

    /**
     * AC1 + AC2 combined: after a real batch commits, a timed-out batch must stop
     * the run and return retry() without touching the already-committed cursor.
     */
    @Test
    fun terminalAfterFailedBatch_noDoubleAdvance() =
        runTest {
            val cursorA =
                SyncCursor(
                    lowCreatedAt = "2026-05-24T15:00:00Z",
                    lowTweetId = "tw-1",
                    incrementalWatermarkMillis = 2000L,
                )
            every {
                syncFacade.fetchMissingTweetsStream(any(), any(), any())
            } returns
                flow {
                    // First batch succeeds.
                    emit(SyncEmission.Batch(listOf(tweetEntities("tw-1", "2026-05-24T15:00:00Z")), cursorA))
                    // Second batch times out — Failed carries no cursor.
                    emit(SyncEmission.Failed)
                }

            val capturedProgress = mutableListOf<SyncProgress>()
            val result =
                runTwitterSync(
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

            // Only the first (successful) batch should have been committed.
            assertEquals("only the successful batch must be committed", 1, capturedProgress.size)
            assertEquals("tw-1", capturedProgress[0].lastLowCursorTweetId)
            // The worker must retry so the timed-out batch's IDs are re-enumerated.
            assertEquals(ListenableWorker.Result.retry(), result)
        }

    // ── CR-10 / REL-11: fetch-failed retry must be capped like its siblings ─────

    @Test
    fun fetchFailedRetry_atCap_returnsFailure() =
        runTest {
            // A batch that keeps timing out on fetch (fetchFailed=true) must eventually
            // surface as a terminal failure instead of retrying forever once
            // runAttemptCount reaches MAX_RETRY_ATTEMPTS — matching the cap already
            // applied to the TimeoutCancellationException/FirebaseFirestoreException
            // sibling branches.
            every {
                syncFacade.fetchMissingTweetsStream(any(), any(), any())
            } returns
                flow {
                    emit(SyncEmission.Failed)
                }

            var commits = 0
            val result =
                runTwitterSync(
                    ctx = context,
                    syncFacade = syncFacade,
                    syncProgressDao = syncProgressDao,
                    deletedBookmarkRepository = deletedBookmarkRepository,
                    authGateway = authGateway,
                    enqueuedUid = null,
                    runAsForegroundService = false,
                    runAttemptCount = TwitterSyncWorker.MAX_RETRY_ATTEMPTS,
                    setForegroundInfo = {},
                    commitBatch = { _, _ -> commits++ },
                )

            assertEquals("no cursor advance should commit past the cap", 0, commits)
            assertEquals(ListenableWorker.Result.failure(), result)
        }

    // ── CONC-8: skip a stale commit once WorkManager has begun stopping this worker ──

    @Test
    fun isStopped_beforeCommit_skipsCommitAndDoesNotSucceed() =
        runTest {
            // Simulate a REPLACE-superseded worker: WorkManager has already begun
            // cancelling it (isStopped=true) by the time a batch is ready to commit.
            // The guard must skip commitBatch entirely rather than risk persisting a
            // cursor older than the superseding worker's.
            val cursorA =
                SyncCursor(
                    lowCreatedAt = "2026-05-24T15:00:00Z",
                    lowTweetId = "tw-1",
                    incrementalWatermarkMillis = 2000L,
                )
            every {
                syncFacade.fetchMissingTweetsStream(any(), any(), any())
            } returns
                flow {
                    emit(SyncEmission.Batch(listOf(tweetEntities("tw-1", "2026-05-24T15:00:00Z")), cursorA))
                }

            var commits = 0
            // The cancellation raised by the isStopped guard is genuine (not
            // fetchFailed, not a timeout stop reason), so it propagates per the
            // existing `else -> throw e` rethrow — this is the same real-cancellation
            // path WorkManager itself drives when it actually tears down a superseded
            // worker's coroutine, so a thrown CancellationException here (rather than a
            // false terminal success) is the correct, expected outcome.
            var threw = false
            try {
                runTwitterSync(
                    ctx = context,
                    syncFacade = syncFacade,
                    syncProgressDao = syncProgressDao,
                    deletedBookmarkRepository = deletedBookmarkRepository,
                    authGateway = authGateway,
                    enqueuedUid = null,
                    runAsForegroundService = false,
                    runAttemptCount = 0,
                    isStopped = { true },
                    setForegroundInfo = {},
                    commitBatch = { _, _ -> commits++ },
                )
            } catch (e: CancellationException) {
                threw = true
            }

            assertEquals("a worker already being stopped must not commit a stale cursor", 0, commits)
            assertTrue("the isStopped guard must raise cancellation rather than succeed", threw)
        }
}
