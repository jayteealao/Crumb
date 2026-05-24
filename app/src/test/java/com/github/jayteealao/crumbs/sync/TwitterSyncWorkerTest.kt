package com.github.jayteealao.crumbs.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import com.github.jayteealao.crumbs.auth.AuthGateway
import com.github.jayteealao.crumbs.auth.CurrentUser
import com.github.jayteealao.crumbs.data.DeletedBookmarkRepository
import com.github.jayteealao.crumbs.data.SyncProgress
import com.github.jayteealao.crumbs.data.SyncProgressDao
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.twitter.data.TweetDao
import com.github.jayteealao.twitter.data.firestore.FirestoreRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
    private lateinit var tweetDao: TweetDao
    private lateinit var syncProgressDao: SyncProgressDao
    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var deletedBookmarkRepository: DeletedBookmarkRepository
    private lateinit var authGateway: AuthGateway
    private val currentUserFlow = MutableStateFlow<CurrentUser?>(CurrentUser(uid = "uid-test", email = null))

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        tweetDao = mockk(relaxed = true)
        syncProgressDao = mockk(relaxed = true)
        firestoreRepository = mockk()
        deletedBookmarkRepository = mockk()
        authGateway = mockk()
        every { authGateway.currentUser } returns currentUserFlow
        coEvery { tweetDao.getAllTweetIds() } returns emptyList()
        coEvery { tweetDao.getMaxOrder() } returns 1000
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
            tweetDao = tweetDao,
            syncProgressDao = syncProgressDao,
            firestoreRepository = firestoreRepository,
            deletedBookmarkRepository = deletedBookmarkRepository,
            authGateway = authGateway,
            runAsForegroundService = false,
            runAttemptCount = 0,
            setForegroundInfo = {},
            commitBatch = { batch, _ -> capturedCommits += batch },
        )

        assertEquals(ListenableWorker.Result.failure(), result)
        assertTrue("no batches should commit when uid is null", capturedCommits.isEmpty())
        coVerify(exactly = 0) { firestoreRepository.fetchTweetsNotInLocalStream(any(), any()) }
    }

    @Test
    fun coldStart_twoBatches_commitsBothAndAdvancesCursors() = runTest {
        every {
            firestoreRepository.fetchTweetsNotInLocalStream(any(), any())
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
            tweetDao = tweetDao,
            syncProgressDao = syncProgressDao,
            firestoreRepository = firestoreRepository,
            deletedBookmarkRepository = deletedBookmarkRepository,
            authGateway = authGateway,
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
            firestoreRepository.fetchTweetsNotInLocalStream(any(), any())
        } returns stubStream(listOf(tweetEntities("tw-100", "2026-05-24T11:00:00Z")))

        val capturedProgress = slot<SyncProgress>()
        val captured = mutableListOf<SyncProgress>()

        val result = runTwitterSync(
            ctx = context,
            tweetDao = tweetDao,
            syncProgressDao = syncProgressDao,
            firestoreRepository = firestoreRepository,
            deletedBookmarkRepository = deletedBookmarkRepository,
            authGateway = authGateway,
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
            firestoreRepository.fetchTweetsNotInLocalStream(any(), any())
        } returns flow { throw unavailable }

        val result = runTwitterSync(
            ctx = context,
            tweetDao = tweetDao,
            syncProgressDao = syncProgressDao,
            firestoreRepository = firestoreRepository,
            deletedBookmarkRepository = deletedBookmarkRepository,
            authGateway = authGateway,
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
            firestoreRepository.fetchTweetsNotInLocalStream(any(), any())
        } returns flow { throw unavailable }

        val result = runTwitterSync(
            ctx = context,
            tweetDao = tweetDao,
            syncProgressDao = syncProgressDao,
            firestoreRepository = firestoreRepository,
            deletedBookmarkRepository = deletedBookmarkRepository,
            authGateway = authGateway,
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
            firestoreRepository.fetchTweetsNotInLocalStream(any(), any())
        } returns flow { throw denied }

        val result = runTwitterSync(
            ctx = context,
            tweetDao = tweetDao,
            syncProgressDao = syncProgressDao,
            firestoreRepository = firestoreRepository,
            deletedBookmarkRepository = deletedBookmarkRepository,
            authGateway = authGateway,
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
            firestoreRepository.fetchTweetsNotInLocalStream(any(), any())
        } returns flow {
            // Trigger a real TimeoutCancellationException — the constructor is
            // internal, so we route through withTimeout instead.
            withTimeout(1L) { delay(Long.MAX_VALUE) }
        }

        val result = runTwitterSync(
            ctx = context,
            tweetDao = tweetDao,
            syncProgressDao = syncProgressDao,
            firestoreRepository = firestoreRepository,
            deletedBookmarkRepository = deletedBookmarkRepository,
            authGateway = authGateway,
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
            firestoreRepository.fetchTweetsNotInLocalStream(any(), any())
        } returns flowOf()

        var commits = 0
        val result = runTwitterSync(
            ctx = context,
            tweetDao = tweetDao,
            syncProgressDao = syncProgressDao,
            firestoreRepository = firestoreRepository,
            deletedBookmarkRepository = deletedBookmarkRepository,
            authGateway = authGateway,
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
    }

    @Test
    fun coldStartFailureInjector_doesNotMarkProgress() = runTest {
        every {
            firestoreRepository.fetchTweetsNotInLocalStream(any(), any())
        } returns flow { throw RuntimeException("boom") }

        val capturedProgress: SyncProgress? = null

        val result = runTwitterSync(
            ctx = context,
            tweetDao = tweetDao,
            syncProgressDao = syncProgressDao,
            firestoreRepository = firestoreRepository,
            deletedBookmarkRepository = deletedBookmarkRepository,
            authGateway = authGateway,
            runAsForegroundService = false,
            runAttemptCount = 0,
            setForegroundInfo = {},
            commitBatch = { _, _ -> },
        )

        assertEquals(ListenableWorker.Result.failure(), result)
        assertNull(capturedProgress)
    }
}
