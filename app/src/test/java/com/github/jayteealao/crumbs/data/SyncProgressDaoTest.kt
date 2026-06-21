package com.github.jayteealao.crumbs.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.jayteealao.crumbs.db.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * In-memory Room round-trip for [SyncProgressDao], including the v20 watermark column
 * (`last_incremental_retrieved_at_ms`). Closes the gap that the DAO previously had only mocked /
 * migration-level coverage — the cursor-narrowed sync now reads and writes it on every run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncProgressDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: SyncProgressDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.syncProgressDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsertThenGet_roundTripsAllColumns_includingWatermark() = runTest {
        val progress = SyncProgress(
            uid = "uid-1",
            lastHighCursorCreatedAt = "2026-06-01T00:00:00Z",
            lastHighCursorTweetId = "tw-high",
            lastLowCursorCreatedAt = "2026-05-01T00:00:00Z",
            lastLowCursorTweetId = "tw-low",
            totalBatchesIngested = 7,
            lastUpdatedAtMs = 1_700_000_000_000L,
            lastIncrementalRetrievedAtMs = 1_699_999_999_000L,
        )
        dao.upsert(progress)

        assertEquals(progress, dao.get("uid-1"))
    }

    @Test
    fun watermark_roundTripsNull_forFreshInstall() = runTest {
        val fresh = SyncProgress(
            uid = "uid-fresh",
            lastHighCursorCreatedAt = null,
            lastHighCursorTweetId = null,
            lastLowCursorCreatedAt = null,
            lastLowCursorTweetId = null,
            totalBatchesIngested = 0,
            lastUpdatedAtMs = 0L,
            lastIncrementalRetrievedAtMs = null,
        )
        dao.upsert(fresh)

        val read = dao.get("uid-fresh")
        assertNull(read?.lastIncrementalRetrievedAtMs)
        assertEquals(fresh, read)
    }

    @Test
    fun upsert_replacesOnConflict_advancingWatermarkAndLowCursor() = runTest {
        val uid = "uid-2"
        dao.upsert(
            SyncProgress(uid, null, null, "2026-05-01T00:00:00Z", "tw-a", 1, 1L, lastIncrementalRetrievedAtMs = 100L),
        )
        // Same uid → REPLACE: the watermark and the low backfill cursor advance.
        dao.upsert(
            SyncProgress(uid, null, null, "2026-04-01T00:00:00Z", "tw-b", 2, 2L, lastIncrementalRetrievedAtMs = 200L),
        )

        val read = dao.get(uid)
        assertEquals(2, read?.totalBatchesIngested)
        assertEquals(200L, read?.lastIncrementalRetrievedAtMs)
        assertEquals("2026-04-01T00:00:00Z", read?.lastLowCursorCreatedAt)
    }

    @Test
    fun get_returnsNull_forUnknownUid() = runTest {
        assertNull(dao.get("nobody"))
    }
}
