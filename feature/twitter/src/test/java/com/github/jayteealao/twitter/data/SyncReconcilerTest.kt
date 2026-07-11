package com.github.jayteealao.twitter.data

import com.github.jayteealao.crumbs.data.DeletedBookmarkRepository
import com.github.jayteealao.twitter.data.firestore.FirestoreRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
 * All Firestore I/O and Room queries are mocked. The only concurrency primitive under test is
 * [java.util.concurrent.atomic.AtomicBoolean.compareAndSet] (the per-session throttle) which
 * is deterministic under [StandardTestDispatcher].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncReconcilerTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var tweetDao: TweetDao
    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var syncEnqueuer: TwitterSyncEnqueuer
    private lateinit var repository: Repository

    // Backing flow for countTombstoneAware — tests can advance it to simulate Room catching up.
    private val countFlow = MutableStateFlow(0)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        tweetDao = mockk(relaxed = true)
        firestoreRepository = mockk(relaxed = true)
        syncEnqueuer = mockk(relaxed = true)
        every { syncEnqueuer.observeIsRunning() } returns flowOf(false)
        every { tweetDao.countTombstoneAware(any()) } returns countFlow

        repository = Repository(
            tweetDao = tweetDao,
            authPref = mockk(relaxed = true),
            firestoreRepository = firestoreRepository,
            deletedBookmarkRepository = mockk(relaxed = true),
            callableService = mockk(relaxed = true),
            scope = CoroutineScope(dispatcher),
            syncEnqueuer = syncEnqueuer,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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

    // AC2: local == server → no sync kicked, isSyncIncomplete = false.
    @Test
    fun noKick_whenLocalEqualsServer() = runTest(dispatcher) {
        coEvery { firestoreRepository.getServerBookmarkCount() } returns Result.success(4000L)
        coEvery { tweetDao.countAllActive() } returns 4000

        repository.reconcileIfIncomplete()
        advanceUntilIdle()

        verify(exactly = 0) { syncEnqueuer.enqueueColdStart() }
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

    // AC4: throttle prevents a second Firestore read and a second WorkManager enqueue.
    @Test
    fun throttle_preventsDoubleKick() = runTest(dispatcher) {
        coEvery { firestoreRepository.getServerBookmarkCount() } returns Result.success(4000L)
        coEvery { tweetDao.countAllActive() } returns 3310

        repository.reconcileIfIncomplete()
        advanceUntilIdle()
        repository.reconcileIfIncomplete()
        advanceUntilIdle()

        coVerify(exactly = 1) { firestoreRepository.getServerBookmarkCount() }
        verify(exactly = 1) { syncEnqueuer.enqueueColdStart() }
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
