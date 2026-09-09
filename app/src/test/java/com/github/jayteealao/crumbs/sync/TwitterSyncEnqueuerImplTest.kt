package com.github.jayteealao.crumbs.sync

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.jayteealao.crumbs.auth.AuthGateway
import com.github.jayteealao.crumbs.auth.CurrentUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Unit tests for [TwitterSyncEnqueuerImpl] — the app-side seam that turns
 * [com.github.jayteealao.twitter.data.TwitterSyncEnqueuer] calls into
 * `WorkManager.enqueueUniqueWork(...)`.
 *
 * `WorkManager.getInstance(Context)` delegates to a Companion instance method
 * (`WorkManager$Companion.getInstance`), which `mockkStatic(WorkManager::class)`
 * cannot cleanly intercept (verified: stubbing it throws
 * `MockKException: Failed matching mocking signature`). So — per the existing
 * codebase convention ([XTokenMigrationWorkerTest]) — this uses the REAL
 * `WorkManager` singleton backed by [WorkManagerTestInitHelper]'s in-memory test
 * database, and asserts the two policies by their observable effect on the
 * unique-work identity rather than by capturing the `enqueueUniqueWork` args
 * (the test WorkManager DB prunes a REPLACE-cancelled request immediately, so
 * history *size* alone doesn't discriminate the policies — the surviving
 * request's *id* does):
 *  - `KEEP` (cold-start): a second enqueue while the first is still pending is a
 *    no-op — the surviving unique-work id is unchanged.
 *  - `REPLACE` (refresh): a second enqueue cancels the first and inserts a new
 *    request — the surviving unique-work id changes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TwitterSyncEnqueuerImplTest {
    private lateinit var context: Context
    private lateinit var authGateway: AuthGateway
    private val currentUserFlow = MutableStateFlow<CurrentUser?>(CurrentUser(uid = "uid-test", email = null))

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )

        authGateway = mockk()
        every { authGateway.currentUser } returns currentUserFlow
    }

    private fun enqueuer() = TwitterSyncEnqueuerImpl(context, authGateway)

    // enqueueUniqueWork's cancel-then-insert (REPLACE) and lookup (KEEP) bookkeeping
    // is dispatched onto WorkManager's internal executor, which posts back to the main
    // looper; Robolectric needs an explicit idle() for that work to actually land before
    // the assertions below query it.
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun uniqueWorkHistory(uid: String): List<WorkInfo> {
        idle()
        return WorkManager.getInstance(context).getWorkInfosForUniqueWork(TwitterSyncWorker.uniqueName(uid)).get()
    }

    /** The surviving (non-CANCELLED, if any) entry for the uid's unique work name. */
    private fun survivingEntry(uid: String): WorkInfo {
        val history = uniqueWorkHistory(uid)
        return history.firstOrNull { it.state != WorkInfo.State.CANCELLED } ?: history.single()
    }

    @Test
    fun enqueueRefresh_usesReplacePolicy_soASecondCallSupersedesTheFirst() {
        enqueuer().enqueueRefresh()
        val first = survivingEntry("uid-test")
        assertEquals(WorkInfo.State.ENQUEUED, first.state)

        enqueuer().enqueueRefresh()
        val second = survivingEntry("uid-test")

        // REPLACE cancels the existing (possibly wedged/frozen) request and inserts a
        // brand-new one in its place — a DIFFERENT work id survives.
        assertTrue(
            "REPLACE must supersede the prior request with a new one, not keep the original",
            first.id != second.id,
        )
        assertEquals(WorkInfo.State.ENQUEUED, second.state)
    }

    @Test
    fun enqueueColdStart_usesKeepPolicy_soASecondCallIsDroppedWhilePending() {
        enqueuer().enqueueColdStart()
        val first = survivingEntry("uid-test")
        assertEquals(WorkInfo.State.ENQUEUED, first.state)

        enqueuer().enqueueColdStart()
        val second = survivingEntry("uid-test")

        // KEEP: back-to-back cold-start triggers coalesce into the SAME pending request.
        assertEquals(
            "KEEP must not replace a still-pending cold-start request",
            first.id,
            second.id,
        )
        assertEquals(WorkInfo.State.ENQUEUED, second.state)
    }

    @Test
    fun enqueue_nullUid_skipsWorkManager_forBothColdStartAndRefresh() {
        currentUserFlow.value = null

        enqueuer().enqueueColdStart()
        enqueuer().enqueueRefresh()

        assertTrue(
            "no work should be enqueued when there is no signed-in uid",
            uniqueWorkHistory("uid-test").isEmpty(),
        )
    }

    @Test
    fun enqueue_emptyUid_skipsWorkManager() {
        currentUserFlow.value = CurrentUser(uid = "", email = null)

        enqueuer().enqueueRefresh()

        assertTrue(
            "an empty (blank) uid must be treated the same as no uid",
            uniqueWorkHistory("").isEmpty(),
        )
    }
}
