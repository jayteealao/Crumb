package com.github.jayteealao.twitter.data.firestore

import com.github.jayteealao.twitter.models.TweetEntities

/**
 * Pure cursor-decision seam for the resumable, cursor-narrowed Twitter sync.
 *
 * The Firestore I/O (enumerate head/tail pages, fetch entities by id) lives in
 * [FirestoreRepository]; everything in this file is side-effect-free so the
 * **no-missed / no-duplicated** invariant — the slice's load-bearing correctness
 * property — is unit-testable in isolation (`FirestoreCursorNarrowingTest`).
 *
 * Two-phase, key-split enumeration:
 *  - **Head** walks `retrievedAt DESC` from the top down to
 *    [SyncCursor.incrementalWatermarkMillis], catching every newly-seen item — including a
 *    newly-bookmarked OLD tweet (fresh `retrievedAt`, old `createdAt`) that a `createdAt`-only
 *    cursor would silently skip.
 *  - **Tail** resumes the historical backfill via `createdAt DESC, __name__ ASC`
 *    `startAfter(lowCreatedAt, lowTweetId)`, and is the ONLY phase that includes legacy
 *    NULL-`retrievedAt` docs (the head's `orderBy(retrievedAt)` silently excludes them).
 *
 * Why the repository computes the cursors (and not the worker, as a single-cursor design
 * might): the worker sees one merged stream and cannot tell a head item from a tail item — and
 * a head item with a low `createdAt` (the new-bookmark-of-an-old-tweet case) must NOT drag the
 * tail's backfill cursor down, or the next run's `startAfter` would skip the un-backfilled
 * middle. Only the phase-aware planner here can advance each cursor from the right docs.
 */

/** Firestore `whereIn` caps a query at 30 ids; fetch batches mirror that. */
internal const val FIRESTORE_WHERE_IN_LIMIT = 30

/** One enumerated tweet doc's sort keys (no entity payload yet). */
internal data class CursorDoc(
    val id: String,
    val retrievedAtMillis: Long?,
    val createdAt: String,
)

/** Which phase produced a batch — for logging + tests. */
internal enum class SyncPhase { HEAD, TAIL, TERMINAL }

/**
 * A planned unit of work: the ids to fetch + the cursor to persist atomically with the batch
 * insert. A [SyncPhase.TERMINAL] plan has empty [ids] and exists only to checkpoint the final
 * cursor (e.g. the advanced watermark on a nothing-new run).
 */
internal data class SyncBatchPlan(
    val ids: List<String>,
    val cursor: SyncCursor,
    val phase: SyncPhase,
)

/**
 * The persistable sync cursor. Mirrors the mutable columns of `SyncProgress`; the worker
 * stamps `uid` / `totalBatchesIngested` / `lastUpdatedAtMs` when it persists.
 *
 * Carried into [FirestoreRepository.fetchMissingTweetsStream] as the prior resume point and
 * emitted back (advanced) with every [SyncEmission].
 */
data class SyncCursor(
    val highCreatedAt: String? = null,
    val highTweetId: String? = null,
    val lowCreatedAt: String? = null,
    val lowTweetId: String? = null,
    val incrementalWatermarkMillis: Long? = null,
)

/**
 * One streamed outcome of [FirestoreRepository.fetchTweetsNotInLocalStream] — a sealed
 * hierarchy so the three logical outcomes (data batch / terminal checkpoint / failed
 * fetch) are exhaustively distinguishable at the collector instead of being encoded
 * across independent `entities` + `fetchFailed` fields, which used to leave invalid
 * combinations (e.g. `fetchFailed=true` WITH non-empty `entities`) representable.
 */
sealed interface SyncEmission {
    /**
     * A normal streamed batch: the fetched aggregates (for a HEAD/TAIL plan) + the
     * cursor to commit atomically with them. [entities] is USUALLY non-empty, but may
     * legitimately come back empty when a plan's ids all vanished between enumeration
     * and fetch (e.g. concurrent deletes) — the collector treats that the same as a
     * [Checkpoint] (cursor persists, no batch-count bump) rather than this being a
     * distinct state.
     */
    data class Batch(
        val entities: List<TweetEntities>,
        val cursor: SyncCursor,
    ) : SyncEmission

    /**
     * The TERMINAL checkpoint — no entities fetched — carrying the advanced cursor
     * (watermark / backfill floor) so a nothing-new run still persists progress.
     */
    data class Checkpoint(
        val cursor: SyncCursor,
    ) : SyncEmission

    /**
     * A batch fetch that timed out. Carries NO cursor — the whole point is that the
     * collector must NOT advance the persisted cursor for a failed batch, so the
     * skipped IDs are re-enumerated on the next run.
     */
    data object Failed : SyncEmission
}

/**
 * Turn the enumerated head + tail docs into an ordered list of [SyncBatchPlan]s with
 * phase-correct cursors. Pure: no Firestore handle, no clock, no I/O.
 *
 * Contract:
 *  - **No missed:** every corpus doc not already in [localIds]/[deletedIds] that the head
 *    (retrievedAt > watermark) or the tail (createdAt below the resume point) enumerated
 *    appears in exactly one plan.
 *  - **No duplicated:** an id present in both phases is emitted once (head wins); no id repeats.
 *  - **Head-first, feed-ordered:** head batches (newest `retrievedAt` first) precede tail
 *    batches; tail batches preserve the `createdAt DESC` enumeration order so the low cursor
 *    advances monotonically and resume is safe.
 *  - **Watermark safety:** the advanced `incrementalWatermarkMillis` (the corpus's max
 *    `retrievedAt`) rides only the LAST head batch onward — never mid-head — so an interrupted
 *    head run never persists a watermark that would skip un-committed items.
 *  - **Tail-resume safety:** the low `(createdAt, tweetId)` cursor advances only from tail
 *    docs, never from head docs (a head item's old `createdAt` must not move the backfill).
 *
 * @param headDocs head enumeration, `retrievedAt DESC` (all non-null retrievedAt).
 * @param tailDocs tail enumeration, `createdAt DESC, __name__ ASC` (may include null retrievedAt).
 */
internal fun planNarrowedSync(
    headDocs: List<CursorDoc>,
    tailDocs: List<CursorDoc>,
    prior: SyncCursor,
    localIds: Set<String>,
    deletedIds: Set<String>,
    batchSize: Int = FIRESTORE_WHERE_IN_LIMIT,
): List<SyncBatchPlan> {
    require(batchSize > 0) { "batchSize must be positive" }

    // Corpus max retrievedAt (the head is retrievedAt-DESC, so its top doc; maxOf is defensive).
    val headMax = headDocs.asSequence().mapNotNull { it.retrievedAtMillis }.maxOrNull()
    val newWatermark = maxOfNullable(prior.incrementalWatermarkMillis, headMax)

    val headSeen = headDocs.mapTo(HashSet(headDocs.size)) { it.id }

    fun skip(id: String) = id in localIds || id in deletedIds

    // Head: drop synced/deleted, de-dup, then feed-sort (retrievedAt DESC, createdAt DESC, id).
    val headMissing =
        headDocs
            .asSequence()
            .filterNot { skip(it.id) }
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<CursorDoc> { it.retrievedAtMillis ?: Long.MIN_VALUE }
                    .thenByDescending { it.createdAt }
                    .thenBy { it.id },
            ).toList()

    // Tail: drop anything already in the head, plus synced/deleted; de-dup. PRESERVE the
    // createdAt-DESC enumeration order (do NOT re-sort) so each batch's last item is the lowest
    // createdAt committed so far — a safe per-batch resume point.
    val tailMissing =
        tailDocs
            .asSequence()
            .filterNot { it.id in headSeen || skip(it.id) }
            .distinctBy { it.id }
            .toList()

    // High cursor is logging-only now (superseded by the watermark). Track the newest createdAt
    // ENUMERATED so the resume log stays meaningful; fall back to the prior value.
    val newestByCreated =
        (headDocs.asSequence() + tailDocs.asSequence())
            .maxWithOrNull(compareBy({ it.createdAt }, { it.id }))
    val highCreatedAt = newestByCreated?.createdAt ?: prior.highCreatedAt
    val highTweetId = newestByCreated?.id ?: prior.highTweetId

    val plans = ArrayList<SyncBatchPlan>()

    val headBatches = headMissing.chunked(batchSize)
    headBatches.forEachIndexed { idx, batch ->
        // The watermark is durable only once the WHOLE head is committed, so it rides only the
        // LAST head batch (head batches 1..n-1 keep the prior watermark; a mid-head crash then
        // re-scans the head next run rather than skipping un-committed items).
        val watermark = if (idx == headBatches.lastIndex) newWatermark else prior.incrementalWatermarkMillis
        plans +=
            SyncBatchPlan(
                ids = batch.map { it.id },
                cursor =
                    SyncCursor(
                        highCreatedAt = highCreatedAt,
                        highTweetId = highTweetId,
                        lowCreatedAt = prior.lowCreatedAt, // head never advances the backfill tail
                        lowTweetId = prior.lowTweetId,
                        incrementalWatermarkMillis = watermark,
                    ),
                phase = SyncPhase.HEAD,
            )
    }

    tailMissing.chunked(batchSize).forEach { batch ->
        val low = batch.last() // lowest createdAt in this createdAt-DESC chunk → safe resume point
        plans +=
            SyncBatchPlan(
                ids = batch.map { it.id },
                cursor =
                    SyncCursor(
                        highCreatedAt = highCreatedAt,
                        highTweetId = highTweetId,
                        lowCreatedAt = low.createdAt,
                        lowTweetId = low.id,
                        // The head is fully enumerated before any tail batch streams, so advancing the
                        // watermark here is safe even when the head produced no missing items.
                        incrementalWatermarkMillis = newWatermark,
                    ),
                phase = SyncPhase.TAIL,
            )
    }

    // Terminal checkpoint: always emitted so a nothing-new run still persists the advanced
    // watermark and the backfill floor. Low advances to the tail enumeration floor (the lowest
    // createdAt READ — everything above it is synced this run, whether committed now or already
    // local), falling back to the prior low when the tail read nothing.
    val tailFloor = tailDocs.lastOrNull()
    plans +=
        SyncBatchPlan(
            ids = emptyList(),
            cursor =
                SyncCursor(
                    highCreatedAt = highCreatedAt,
                    highTweetId = highTweetId,
                    lowCreatedAt = tailFloor?.createdAt ?: prior.lowCreatedAt,
                    lowTweetId = tailFloor?.id ?: prior.lowTweetId,
                    incrementalWatermarkMillis = newWatermark,
                ),
            phase = SyncPhase.TERMINAL,
        )
    return plans
}

/** max() that treats null as "absent" (not negative-infinity). */
private fun maxOfNullable(
    a: Long?,
    b: Long?,
): Long? =
    when {
        a == null -> b
        b == null -> a
        else -> maxOf(a, b)
    }
