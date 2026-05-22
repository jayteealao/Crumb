package com.github.jayteealao.twitter.data

import com.github.jayteealao.crumbs.data.DeletedBookmarkRepository
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.twitter.data.firestore.FirestoreRepository
import com.google.firebase.functions.FirebaseFunctions
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies Repository.confirmDeletePending + cancelDeletePending compose the
 * Room/tombstone and Firestore writes in the documented order and survive
 * Firestore-side failures without propagating them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SwipeHandlerTest {

    private lateinit var tweetDao: TweetDao
    private lateinit var prefs: Prefs
    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var deletedBookmarkRepository: DeletedBookmarkRepository
    private lateinit var functions: FirebaseFunctions
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        tweetDao = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        firestoreRepository = mockk()
        deletedBookmarkRepository = mockk()
        functions = mockk(relaxed = true)
        scope = TestScope(StandardTestDispatcher())
    }

    private fun newRepository(): Repository = Repository(
        tweetDao = tweetDao,
        authPref = prefs,
        firestoreRepository = firestoreRepository,
        deletedBookmarkRepository = deletedBookmarkRepository,
        functions = functions,
        scope = scope,
    )

    @Test
    fun confirmDeletePending_writesTombstoneAndFirestoreMark() = runTest {
        coEvery { deletedBookmarkRepository.softDelete("t-1", BookmarkSource.Twitter) } just Runs
        coEvery { firestoreRepository.markDeleted("t-1") } just Runs

        newRepository().confirmDeletePending("t-1")

        coVerify(exactly = 1) { deletedBookmarkRepository.softDelete("t-1", BookmarkSource.Twitter) }
        coVerify(exactly = 1) { firestoreRepository.markDeleted("t-1") }
    }

    @Test
    fun cancelDeletePending_writesRoomBeforeFirestore() = runTest {
        coEvery { tweetDao.updatePendingDelete("t-2", false) } just Runs
        coEvery { firestoreRepository.cancelPendingDelete("t-2") } just Runs

        newRepository().cancelDeletePending("t-2")

        // Room first so the visible state flips immediately even if Firestore is offline.
        coVerifyOrder {
            tweetDao.updatePendingDelete("t-2", false)
            firestoreRepository.cancelPendingDelete("t-2")
        }
    }

    @Test
    fun confirmDeletePending_swallowsFirestoreError() = runTest {
        coEvery { deletedBookmarkRepository.softDelete("t-3", BookmarkSource.Twitter) } just Runs
        coEvery { firestoreRepository.markDeleted("t-3") } throws RuntimeException("network down")

        // Does not throw — tombstone is the source of UI truth.
        newRepository().confirmDeletePending("t-3")

        coVerify(exactly = 1) { deletedBookmarkRepository.softDelete("t-3", BookmarkSource.Twitter) }
        coVerify(exactly = 1) { firestoreRepository.markDeleted("t-3") }
    }

    @Test
    fun cancelDeletePending_swallowsFirestoreError() = runTest {
        coEvery { tweetDao.updatePendingDelete("t-4", false) } just Runs
        coEvery { firestoreRepository.cancelPendingDelete("t-4") } throws RuntimeException("offline")

        // Does not throw — Room update is the source of UI truth.
        newRepository().cancelDeletePending("t-4")

        coVerify(exactly = 1) { tweetDao.updatePendingDelete("t-4", false) }
        coVerify(exactly = 1) { firestoreRepository.cancelPendingDelete("t-4") }
    }
}
