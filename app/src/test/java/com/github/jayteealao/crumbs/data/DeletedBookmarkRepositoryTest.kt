package com.github.jayteealao.crumbs.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.jayteealao.crumbs.db.AppDatabase
import com.github.jayteealao.crumbs.models.BookmarkSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Validates the soft-delete tombstone round-trip in [DeletedBookmarkRepository].
 *
 * AC: when the snackbar timer expires without UNDO, the tombstoned bookmark id is
 * filtered out of the next sync. The Twitter/Reddit sync paths gate on
 * `deletedBookmarkRepository.isDeleted(id)`; these tests prove that contract from
 * the data-layer side: softDelete writes a tombstone that isDeleted observes, and
 * undoDelete clears it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeletedBookmarkRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var snackbarBus: SnackbarBus
    private lateinit var repo: DeletedBookmarkRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        snackbarBus = SnackbarBus()
        repo = DeletedBookmarkRepository(db.deletedBookmarkDao(), snackbarBus)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun softDelete_insertsTombstone_isDeletedReturnsTrue() = runTest {
        repo.softDelete("tweet-123", BookmarkSource.Twitter)

        assertTrue(
            "Tombstoned id should be reported as deleted so sync filters it out",
            repo.isDeleted("tweet-123", BookmarkSource.Twitter),
        )
        assertFalse(
            "Untouched id should not be reported as deleted",
            repo.isDeleted("other-id", BookmarkSource.Twitter),
        )
    }

    @Test
    fun undoDelete_removesTombstone_isDeletedReturnsFalse() = runTest {
        repo.softDelete("tweet-456", BookmarkSource.Twitter)
        assertTrue(repo.isDeleted("tweet-456", BookmarkSource.Twitter))

        repo.undoDelete("tweet-456", BookmarkSource.Twitter)

        assertFalse(
            "UNDO must clear the tombstone so the bookmark re-appears in sync",
            repo.isDeleted("tweet-456", BookmarkSource.Twitter),
        )
    }

    @Test
    fun softDelete_emitsUndoableDeleteEvent() = runTest(UnconfinedTestDispatcher()) {
        // snackbarBus.events is a MutableSharedFlow with replay = 0, so the collector
        // must be subscribed before the emission. UnconfinedTestDispatcher starts the
        // async block eagerly (before the next suspension point), guaranteeing the
        // collector is active when softDelete emits — no yield() busy-waits needed.
        val deferredEvent = async { snackbarBus.events.first() }

        repo.softDelete("reddit-abc", BookmarkSource.Reddit)

        // Drain any pending coroutine work (e.g. the collector processing the emission).
        advanceUntilIdle()

        val event = deferredEvent.await()

        assertTrue(
            "Expected UndoableDelete but got ${event::class.simpleName}",
            event is SnackbarEvent.UndoableDelete,
        )
        val undoable = event as SnackbarEvent.UndoableDelete
        assertEquals("reddit-abc", undoable.id)
        assertEquals(BookmarkSource.Reddit, undoable.source)
    }
}
