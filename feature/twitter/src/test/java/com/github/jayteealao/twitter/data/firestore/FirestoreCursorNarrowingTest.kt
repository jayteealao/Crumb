package com.github.jayteealao.twitter.data.firestore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for [planNarrowedSync] — the cursor-decision seam of the resumable,
 * cursor-narrowed sync.
 *
 * The Firestore I/O (head/tail enumeration, `startAfter`, `Source.SERVER`) lives in
 * [FirestoreRepository] and is exercised on-device via logcat per the slice's interactive
 * ACs. Everything assertable WITHOUT a network — the **no-missed / no-duplicated** invariant
 * (R1), head-first phase ordering, head/tail dedup, and phase-correct cursor advancement — is
 * proven here.
 */
class FirestoreCursorNarrowingTest {

    private fun doc(id: String, retrievedAt: Long?, createdAt: String) =
        CursorDoc(id = id, retrievedAtMillis = retrievedAt, createdAt = createdAt)

    private fun List<SyncBatchPlan>.fetchPlans() = filter { it.phase != SyncPhase.TERMINAL }
    private fun List<SyncBatchPlan>.terminal() = single { it.phase == SyncPhase.TERMINAL }
    private fun List<SyncBatchPlan>.allFetchedIds() = fetchPlans().flatMap { it.ids }

    @Test
    fun newWatermark_isMaxOfPriorAndHeadTop() {
        val head = listOf(doc("h1", 500, "c5"), doc("h2", 400, "c4"))
        val plans = planNarrowedSync(head, emptyList(), SyncCursor(incrementalWatermarkMillis = 350), emptySet(), emptySet())
        assertEquals(500L, plans.terminal().cursor.incrementalWatermarkMillis)
    }

    @Test
    fun nullPriorWatermark_seedsFromHeadTop() {
        val head = listOf(doc("h1", 500, "c5"), doc("h2", 400, "c4"))
        val plans = planNarrowedSync(head, emptyList(), SyncCursor(), emptySet(), emptySet())
        assertEquals(500L, plans.terminal().cursor.incrementalWatermarkMillis)
    }

    @Test
    fun emptyEverything_emitsOnlyTerminal_carryingPrior() {
        val prior = SyncCursor(lowCreatedAt = "c-low", lowTweetId = "t-low", incrementalWatermarkMillis = 99)
        val plans = planNarrowedSync(emptyList(), emptyList(), prior, emptySet(), emptySet())
        assertEquals(1, plans.size)
        val terminal = plans.terminal()
        assertTrue(terminal.ids.isEmpty())
        assertEquals("c-low", terminal.cursor.lowCreatedAt)
        assertEquals("t-low", terminal.cursor.lowTweetId)
        assertEquals(99L, terminal.cursor.incrementalWatermarkMillis)
    }

    @Test
    fun headBatchesPrecedeTailBatches() {
        val head = listOf(doc("h1", 500, "2025-01-10"))
        val tail = listOf(doc("t1", 200, "2024-01-01"))
        val fetch = planNarrowedSync(head, tail, SyncCursor(), emptySet(), emptySet()).fetchPlans()
        assertEquals(SyncPhase.HEAD, fetch.first().phase)
        assertEquals(listOf("h1"), fetch.first().ids)
        assertEquals(SyncPhase.TAIL, fetch[1].phase)
        assertEquals(listOf("t1"), fetch[1].ids)
    }

    @Test
    fun headFeedSorted_retrievedAtDesc() {
        // Provided out of order; the planner must feed-sort the head by retrievedAt DESC.
        val head = listOf(doc("h-mid", 400, "c2"), doc("h-top", 500, "c1"), doc("h-low", 300, "c3"))
        val ids = planNarrowedSync(head, emptyList(), SyncCursor(), emptySet(), emptySet(), batchSize = 1)
            .fetchPlans().flatMap { it.ids }
        assertEquals(listOf("h-top", "h-mid", "h-low"), ids)
    }

    @Test
    fun tailPreservesCreatedAtDescEnumerationOrder() {
        // Tail is given in createdAt DESC; the planner must NOT re-sort it (safe low-cursor advance).
        val tail = listOf(doc("t1", 300, "2024-06-01"), doc("t2", null, "2019-05-05"), doc("t3", 200, "2018-03-03"))
        val ids = planNarrowedSync(emptyList(), tail, SyncCursor(), emptySet(), emptySet(), batchSize = 1)
            .fetchPlans().flatMap { it.ids }
        assertEquals(listOf("t1", "t2", "t3"), ids)
    }

    @Test
    fun dedup_headWinsOverTail() {
        val head = listOf(doc("shared", 450, "2020-01-01"))
        val tail = listOf(doc("shared", 450, "2020-01-01"), doc("t1", 200, "2018-01-01"))
        val ids = planNarrowedSync(head, tail, SyncCursor(), emptySet(), emptySet()).allFetchedIds()
        assertEquals(listOf("shared", "t1"), ids) // shared appears once, from the head
        assertEquals(ids.distinct(), ids)
    }

    @Test
    fun localAndDeleted_excludedFromFetch_butFloorStillCoversThem() {
        val head = listOf(doc("h1", 500, "2025-01-10"))
        val tail = listOf(
            doc("t1", 200, "2024-01-01"),
            doc("local1", null, "2020-01-01"),
            doc("del1", null, "2017-01-01"),
        )
        val plans = planNarrowedSync(head, tail, SyncCursor(), setOf("local1"), setOf("del1"))
        val ids = plans.allFetchedIds()
        assertTrue("local1" !in ids)
        assertTrue("del1" !in ids)
        assertEquals(listOf("h1", "t1"), ids)
        // The floor advances past the enumerated-but-excluded docs — everything READ is synced.
        assertEquals("2017-01-01", plans.terminal().cursor.lowCreatedAt)
        assertEquals("del1", plans.terminal().cursor.lowTweetId)
    }

    @Test
    fun watermarkRidesOnlyLastHeadBatch_neverMidHead() {
        val head = listOf(doc("h1", 500, "c1"), doc("h2", 400, "c2"), doc("h3", 300, "c3"))
        val prior = SyncCursor(incrementalWatermarkMillis = 100)
        val headBatches = planNarrowedSync(head, emptyList(), prior, emptySet(), emptySet(), batchSize = 1).fetchPlans()
        assertEquals(3, headBatches.size)
        // Head batches 1..n-1 keep the PRIOR watermark; only the last advances it — so an
        // interrupted head run never persists a watermark that would skip un-committed items.
        assertEquals(100L, headBatches[0].cursor.incrementalWatermarkMillis)
        assertEquals(100L, headBatches[1].cursor.incrementalWatermarkMillis)
        assertEquals(500L, headBatches[2].cursor.incrementalWatermarkMillis)
    }

    @Test
    fun tailLowCursor_advancesToEachBatchLastItem() {
        val tail = listOf(doc("t1", null, "2024-06-01"), doc("t2", null, "2024-05-01"), doc("t3", null, "2024-04-01"))
        val tailBatches = planNarrowedSync(emptyList(), tail, SyncCursor(), emptySet(), emptySet(), batchSize = 2).fetchPlans()
        // batch [t1,t2] → low = t2 (lowest createdAt in this createdAt-DESC chunk); batch [t3] → low = t3.
        assertEquals("2024-05-01", tailBatches[0].cursor.lowCreatedAt)
        assertEquals("t2", tailBatches[0].cursor.lowTweetId)
        assertEquals("2024-04-01", tailBatches[1].cursor.lowCreatedAt)
        assertEquals("t3", tailBatches[1].cursor.lowTweetId)
    }

    @Test
    fun headItemWithOldCreatedAt_doesNotMoveBackfillCursor() {
        // R1 REGRESSION GUARD: a newly-bookmarked OLD tweet (fresh retrievedAt, ancient createdAt)
        // is caught by the head — it must NOT drag the backfill low cursor down into the
        // un-enumerated middle, or the next run's tail `startAfter` would skip that range.
        val head = listOf(doc("old-new", 999, "2008-01-01")) // ancient createdAt, newest retrievedAt
        val tail = listOf(doc("t1", 300, "2024-06-01"), doc("t2", 200, "2024-05-01"))
        val prior = SyncCursor(lowCreatedAt = "2024-07-01", lowTweetId = "prev", incrementalWatermarkMillis = 100)
        val plans = planNarrowedSync(head, tail, prior, emptySet(), emptySet())

        val headBatch = plans.fetchPlans().first { it.phase == SyncPhase.HEAD }
        // The head batch carries the PRIOR low cursor — the ancient createdAt did not leak into it.
        assertEquals("2024-07-01", headBatch.cursor.lowCreatedAt)
        assertEquals("prev", headBatch.cursor.lowTweetId)
        // The backfill low only ever advances from TAIL docs (here down to t2).
        assertEquals("2024-05-01", plans.terminal().cursor.lowCreatedAt)
        assertTrue(
            "the ancient head createdAt must never become the low cursor",
            plans.none { it.cursor.lowCreatedAt == "2008-01-01" },
        )
    }

    @Test
    fun noMissed_noDuplicated_invariant_withLegacyNullAndNewOldTweet() {
        // One corpus hitting every tricky case at once:
        //  - new-bookmark-of-OLD-tweet (old-new): high retrievedAt → head; old createdAt → also
        //    surfaces in the tail enumeration → must dedup to the head, must not move the low cursor.
        //  - legacy NULL-retrievedAt doc (t2): only the tail includes it.
        //  - a local doc + a deleted doc interleaved in the tail: excluded from fetch, still covered.
        val head = listOf(
            doc("h1", 500, "2025-01-10"),
            doc("old-new", 450, "2020-01-01"),
            doc("h2", 400, "2025-01-09"),
        )
        val tail = listOf(
            doc("t1", 300, "2024-06-01"),
            doc("old-new", 450, "2020-01-01"), // DUP with the head
            doc("t2", null, "2019-05-05"), // legacy null-retrievedAt
            doc("local1", null, "2018-06-06"), // already local
            doc("del1", null, "2017-06-06"), // tombstoned
            doc("t3", 200, "2017-03-03"),
        )
        val prior = SyncCursor(lowCreatedAt = "2024-12-01", lowTweetId = "x", incrementalWatermarkMillis = 350)
        val plans = planNarrowedSync(head, tail, prior, setOf("local1"), setOf("del1"))

        val fetchedIds = plans.allFetchedIds()
        // No duplicates anywhere.
        assertEquals(fetchedIds.distinct(), fetchedIds)
        // Exactly the syncable set is fetched (everything enumerated minus local/deleted, deduped).
        assertEquals(setOf("h1", "old-new", "h2", "t1", "t2", "t3"), fetchedIds.toSet())
        // No-missed: every enumerated doc is fetched OR local OR deleted — none silently dropped.
        val covered = fetchedIds.toSet() + "local1" + "del1"
        (head + tail).forEach { assertTrue("doc ${it.id} must be covered", it.id in covered) }
        // Watermark advanced to the corpus max retrievedAt.
        assertEquals(500L, plans.terminal().cursor.incrementalWatermarkMillis)
        // Head-first: all HEAD ids precede all TAIL ids in the fetch order.
        val phases = plans.fetchPlans().map { it.phase }
        assertEquals(phases.filter { it == SyncPhase.HEAD } + phases.filter { it == SyncPhase.TAIL }, phases)
    }

    @Test
    fun fullySynced_nothingMissing_stillCheckpointsAdvancedWatermarkAndFloor() {
        // AC2: a fully-synced corpus — head + tail enumerate docs but ALL are already local —
        // produces no fetch batches, yet the terminal still advances the watermark + floor so the
        // next run reads near-zero.
        val head = listOf(doc("h1", 500, "2025-01-10"))
        val tail = listOf(doc("h1", 500, "2025-01-10"), doc("t-old", 100, "2018-01-01"))
        val plans = planNarrowedSync(head, tail, SyncCursor(incrementalWatermarkMillis = 50), setOf("h1", "t-old"), emptySet())
        assertTrue(plans.allFetchedIds().isEmpty())
        val terminal = plans.terminal()
        assertEquals(500L, terminal.cursor.incrementalWatermarkMillis)
        assertEquals("2018-01-01", terminal.cursor.lowCreatedAt) // floor advanced past the synced tail
        assertEquals("t-old", terminal.cursor.lowTweetId)
    }
}
