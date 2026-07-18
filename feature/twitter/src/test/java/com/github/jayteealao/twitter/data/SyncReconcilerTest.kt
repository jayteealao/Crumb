package com.github.jayteealao.twitter.data

import com.github.jayteealao.crumbs.data.DeletedBookmarkRepository
import com.github.jayteealao.crumbs.data.SyncProgress
import com.github.jayteealao.crumbs.data.SyncProgressDao
import com.github.jayteealao.twitter.data.firestore.FirestoreRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [Repository.reconcileIfIncomplete] — the per-session completeness gate that
 * compares local Room count against the Firestore aggregate total and re-kicks the sync when
 * the gap exceeds [Repository.RECONCILIATION_TOLERANCE] (5).
 *
 * All Firestore I/O and Room queries are mocked. The gate is a per-uid [java.util.Set]
 * (`reconciledUids`), so [auth]'s `currentUser.uid` drives both the "already checked this
 * session" throttle and which uid the rewound [SyncProgress] cursor is upserted for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncReconcilerTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var tweetDao: TweetDao
    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var syncEnqueuer: TwitterSyncEnqueuer
    private lateinit var syncProgressDao: SyncProgressDao
    private lateinit var auth: FirebaseAuth
    private lateinit var repository: Repository

    // Backing flow for countTombstoneAware — tests can advance it to simulate Room catching up.
    private val countFlow = MutableStateFlow(0)

    private fun stubUid(uid: String) {
        val user = mockk<FirebaseUser>()
        every { user.uid } returns uid
        every { auth.currentUser } returns user
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        tweetDao = mockk(relaxed = true)
        firestoreRepository = mockk(relaxed = true)
        syncEnqueuer = mockk(relaxed = true)
        syncProgressDao = mockk(relaxed = true)
        auth = mockk(relaxed = true)
        every { syncEnqueuer.observeIsRunning() } returns flowOf(false)
        every { tweetDao.countTombstoneAware(any()) } returns countFlow
        // Default: a stable signed-in uid. reconcileIfIncomplete() short-circuits as
        // "not authenticated" without this.
        stubUid("test-uid")

        repository = Repository(
            tweetDao = tweetDao,
            authPref = mockk(relaxed = true),
            firestoreRepository = firestoreRepository,
            deletedBookmarkRepository = mockk(relaxed = true),
            callableService = mockk(relaxed = true),
            scope = CoroutineScope(dispatcher),
            syncEnqueuer = syncEnqueuer,
            syncProgressDao = syncProgressDao,
            auth = auth,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Not authenticated → short-circuits without touching Firestore/Room/WorkManager.
    @Test
    fun notAuthenticated_returnsFalse_withoutQuerying() = runTest(dispatcher) {
        every { auth.currentUser } returns null

        val result = repository.reconcileIfIncomplete()
        advanceUntilIdle()

        assertFalse(result)
        coVerify(exactly = 0) { firestoreRepository.getServerBookmarkCount() }
        verify(exactly = 0) { syncEnqueuer.enqueueColdStart() }
    }

    // AC1 (automated): gap > tolerance → sync kicked, isSyncIncomplete = true.
    @Test
    fun kicksSync_whenGapExceedsTolerance() = runTest(dispatcher) {
        coEvery { firestoreRepository.getServerBookmarkCount() } returns Result.success(4000L)
        coEvery { tweetDao.countAllActive() } returns 3310

        repository.reconcileIfIncomplete()
        advanceUntilIdle()

        verify(exactly = 1) { syncEnqueuer.enqueueColdStart() }
        assertTrue(repository.isSyncIncomplete.value)
    }

    // The refill path must null the cursor via syncProgressDao.upsert(...) BEFORE
    // enqueueColdStart() so a corpus that already finished backfilling re-enumerates
    // from scratch instead of being treated as already-covered.
    @Test
    fun kicksSync_nullsCursorViaUpsert_beforeEnqueueingColdStart() = runTest(dispatcher) {
        coEvery { firestoreRepository.getServerBookmarkCount() } returns Result.success(4000L)
        coEvery { tweetDao.countAllActive() } returns 3310

        repository.reconcileIfIncomplete()
        advanceUntilIdle()

        val progressSlot = slot<SyncProgress>()
        coVerify(exactly = 1) { syncProgressDao.upsert(capture(progressSlot)) }
        val persisted = progressSlot.captured
        assertTrue("cursor rewind must target the reconciled uid", persisted.uid == "test-uid")
        assertNull(persisted.lastHighCursorCreatedAt)
        assertNull(persisted.lastHighCursorTweetId)
        assertNull(persisted.lastLowCursorCreatedAt)
        assertNull(persisted.lastLowCursorTweetId)
        assertTrue(persisted.totalBatchesIngested == 0)
        assertNull(persisted.lastIncrementalRetrievedAtMs)

        // Ordering: the cursor must be nulled before the cold-start enqueue.
        coVerifyOrder {
            syncProgressDao.upsert(any())
            syncEnqueuer.enqueueColdStart()
        }
    }

    // AC2: local == server → no sync kicked, isSyncIncomplete = false.
    @Test
    fun noKick_whenLocalEqualsServer() = runTest(dispatcher) {
        coEvery { firestoreRepository.getServerBookmarkCount() } returns Result.success(4000L)
        coEvery { tweetDao.countAllActive() } returns 4000

        repository.reconcileIfIncomplete()
        advanceUntilIdle()

        verify(exactly = 0) { syncEnqueuer.enqueueColdStart() }
        coVerify(exactly = 0) { syncProgressDao.upsert(any()) }
        assertFalse(repository.isSyncIncomplete.value)
    }

    // AC5: gap within tolerance (gap = 4 ≤ 5) → no sync kicked.
    @Test
    fun noKick_whenGapWithinTolerance() = runTest(dispatcher) {
        coEvery { firestoreRepository.getServerBookmarkCount() } returns Result.success(4000L)
        coEvery { tweetDao.countAllActive() } returns 3996 // gap = 4

        repository.reconcileIfIncomplete()
        advanceUntilIdle()

        verify(exactly = 0) { syncEnqueuer.enqueueColdStart() }
        assertFalse(repository.isSyncIncomplete.value)
    }

    // AC4: throttle prevents a second Firestore read and a second WorkManager enqueue
    // for the SAME uid.
    @Test
    fun throttle_preventsDoubleKick_forSameUid() = runTest(dispatcher) {
        coEvery { firestoreRepository.getServerBookmarkCount() } returns Result.success(4000L)
        coEvery { tweetDao.countAllActive() } returns 3310

        repository.reconcileIfIncomplete()
        advanceUntilIdle()
        repository.reconcileIfIncomplete()
        advanceUntilIdle()

        coVerify(exactly = 1) { firestoreRepository.getServerBookmarkCount() }
        verify(exactly = 1) { syncEnqueuer.enqueueColdStart() }
    }

    // Per-uid gate: a DIFFERENT uid (e.g. an in-process account switch) is NOT throttled
    // by a prior uid's check and gets its own reconcile pass.
    @Test
    fun differentUid_isNotThrottled_byPriorUidsCheck() = runTest(dispatcher) {
        coEvery { firestoreRepository.getServerBookmarkCount() } returns Result.success(4000L)
        coEvery { tweetDao.countAllActive() } returns 3310

        stubUid("uid-a")
        repository.reconcileIfIncomplete()
        advanceUntilIdle()

        stubUid("uid-b")
        repository.reconcileIfIncomplete()
        advanceUntilIdle()

        coVerify(exactly = 2) { firestoreRepository.getServerBookmarkCount() }
        verify(exactly = 2) { syncEnqueuer.enqueueColdStart() }
    }

    // Query failure → gate is reset so the next cold-start can retry.
    @Test
    fun queryFailure_allowsRetry() = runTest(dispatcher) {
        val boom = RuntimeException("network error")
        coEvery { firestoreRepository.getServerBookmarkCount() } returns Result.failure(boom)

        val firstResult = repository.reconcileIfIncomplete()
        advanceUntilIdle()
        assertFalse(firstResult)

        // Gate was reset — a second call should attempt the query again.
        coEvery { firestoreRepository.getServerBookmarkCount() } returns Result.success(4000L)
        coEvery { tweetDao.countAllActive() } returns 3310

        repository.reconcileIfIncomplete()
        advanceUntilIdle()

        coVerify(exactly = 2) { firestoreRepository.getServerBookmarkCount() }
        verify(exactly = 1) { syncEnqueuer.enqueueColdStart() }
    }

    // Auto-clear: once the Room count flow emits >= target, isSyncIncomplete flips to false.
    // The auto-clear watcher runs on Dispatchers.IO (real thread pool), so the test waits for
    // the signal via Flow.first with a timeout rather than advanceUntilIdle (test-dispatcher only).
    @Test
    fun autoClear_whenLocalCountCrossesThreshold() = runTest(dispatcher) {
        coEvery { firestoreRepository.getServerBookmarkCount() } returns Result.success(4000L)
        coEvery { tweetDao.countAllActive() } returns 3310

        repository.reconcileIfIncomplete()
        advanceUntilIdle()

        assertTrue(repository.isSyncIncomplete.value)

        // Simulate Room count reaching the server threshold (4000 - tolerance 5 = 3995).
        countFlow.value = 3996
        // The auto-clear watcher on Dispatchers.IO will pick up the emission and clear the signal.
        // Wait up to 2 seconds for the reactive clear to propagate.
        withTimeout(2_000) {
            repository.isSyncIncomplete.first { !it }
        }

        assertFalse(repository.isSyncIncomplete.value)
    }
}
