package com.github.jayteealao.crumbs.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.jayteealao.crumbs.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeletedBookmarkRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: DeletedBookmarkRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = DeletedBookmarkRepository(db.deletedBookmarkDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun softDelete_insertsTombstone_isDeletedReturnsTrue() = runBlocking {
        repo.softDelete("tweet-123", BookmarkSource.TWITTER)

        assertTrue(
            "Tombstoned id should be reported as deleted so sync filters it out",
            repo.isDeleted("tweet-123"),
        )
        assertFalse(
            "Untouched id should not be reported as deleted",
            repo.isDeleted("other-id"),
        )
    }

    @Test
    fun undoDelete_removesTombstone_isDeletedReturnsFalse() = runBlocking {
        repo.softDelete("tweet-456", BookmarkSource.TWITTER)
        assertTrue(repo.isDeleted("tweet-456"))

        repo.undoDelete("tweet-456")

        assertFalse(
            "UNDO must clear the tombstone so the bookmark re-appears in sync",
            repo.isDeleted("tweet-456"),
        )
    }

    @Test
    fun softDelete_emitsUndoableDeleteEvent() = runBlocking {
        // events is a MutableSharedFlow with replay = 0, so the collector must be
        // started before the emission. Launch the collector first, yield to let it
        // subscribe, then trigger softDelete.
        val scope = CoroutineScope(Dispatchers.Default)
        val deferredEvent = scope.async { repo.events.first() }
        // Give the collector a chance to subscribe before we emit.
        yield()
        // Small busy-wait fallback in case the dispatcher hasn't scheduled the
        // collector yet (sharedFlow.first() must be active when tryEmit runs).
        repeat(20) { yield() }

        repo.softDelete("reddit-abc", BookmarkSource.REDDIT)

        val event = deferredEvent.await()

        assertTrue(
            "Expected UndoableDelete but got ${event::class.simpleName}",
            event is SnackbarEvent.UndoableDelete,
        )
        val undoable = event as SnackbarEvent.UndoableDelete
        assertEquals("reddit-abc", undoable.id)
        assertEquals(BookmarkSource.REDDIT, undoable.source)
    }
}
