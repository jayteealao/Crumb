package com.github.jayteealao.twitter.data.firestore

import com.github.jayteealao.twitter.models.TweetEntities
import com.github.jayteealao.twitter.models.TweetEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class FirestoreRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    companion object {
        private const val USERS_ROOT = "users"
        private const val TWEETS_COLLECTION = "tweets"
        private const val TWITTER_USERS_COLLECTION = "twitter_users"
        private const val MEDIA_COLLECTION = "media"
        private const val METRICS_COLLECTION = "metrics"
        private const val INCLUDES_COLLECTION = "includes"
        private const val TEXT_ANNOTATIONS_COLLECTION = "textAnnotations"
        private const val BATCH_SIZE = 500
        private const val MAX_BOOKMARK_READ = 10_000
        private const val READ_PAGE_SIZE = 500
        private const val MAX_PAGE_HOPS = 50
        // Was bumped 30s → 120s to ride out the CustomClassMapper warning storm
        // (~50 unknown-snake_case-key warnings per metrics doc, 1500+ logcat lines
        // per batch serialized on the IO thread) that stalled deserialization on
        // mid-range devices. That storm is now silenced at the source —
        // @IgnoreExtraProperties on the FirestoreModels POJOs — so the budget comes
        // back down to 60s with generous headroom. A timeout here is non-destructive:
        // the per-batch atomic commit + the resume cursor make a retried run continue
        // from the last committed batch. On-device `batch_deser_ms` validates the margin.
        private const val BATCH_TIMEOUT_MS = 60_000L
        // Per-sub-collection timeout: one slow collection cannot starve the others.
        private const val SUB_FETCH_TIMEOUT_MS = 30_000L
        // Enumeration page-read timeout: a stalled Firestore socket during the
        // head/tail page walks cannot freeze the run indefinitely.  Chosen to be
        // at most a sub-collection fetch (SUB_FETCH_TIMEOUT_MS) — a bare page
        // read should be faster than a full entity assembly.  On timeout the
        // TimeoutCancellationException propagates to the worker's outer catch,
        // which returns Result.retry() without advancing the cursor.
        private const val ENUM_PAGE_TIMEOUT_MS = 30_000L
        // Aggregate count() RPCs are single-value server round-trips (no document
        // data transferred) — much cheaper than a page/sub-collection fetch, so a
        // short, independent budget bounds a hung count query without stealing the
        // longer budgets meant for real document reads.
        private const val AGGREGATE_COUNT_TIMEOUT_MS = 15_000L
    }

    private fun requireUid(): String =
        auth.currentUser?.uid
            ?: error("FirestoreRepository called before authentication")

    private fun tweetsCol(uid: String): CollectionReference =
        db.collection(USERS_ROOT).document(uid).collection(TWEETS_COLLECTION)

    private fun twitterUsersCol(uid: String): CollectionReference =
        db.collection(USERS_ROOT).document(uid).collection(TWITTER_USERS_COLLECTION)

    private fun mediaCol(uid: String): CollectionReference =
        db.collection(USERS_ROOT).document(uid).collection(MEDIA_COLLECTION)

    private fun metricsCol(uid: String): CollectionReference =
        db.collection(USERS_ROOT).document(uid).collection(METRICS_COLLECTION)

    private fun includesCol(uid: String): CollectionReference =
        db.collection(USERS_ROOT).document(uid).collection(INCLUDES_COLLECTION)

    private fun textAnnotationsCol(uid: String): CollectionReference =
        db.collection(USERS_ROOT).document(uid).collection(TEXT_ANNOTATIONS_COLLECTION)

    /**
     * Fetch all tweet IDs from the signed-in user's tweets sub-collection.
     *
     * Pagination uses `FieldPath.documentId()`. Snowflake IDs of mixed lengths
     * (18-char 2017-era vs 19-char 2024+) lex-compare incorrectly; this is
     * acceptable for the active corpus (all 19-char) but is flagged forward to
     * a future cleanup. See plan Risks/Watchouts for `android-reader`.
     */
    suspend fun getAllTweetIds(): Set<String> = withContext(Dispatchers.IO) {
        val uid = requireUid()
        Timber.w("getAllTweetIds: pagination by documentId() is lex-ordered; mixed-length snowflake IDs may yield non-monotonic boundaries")
        try {
            Timber.d("Fetching tweet IDs from Firestore (max=$MAX_BOOKMARK_READ)")
            val ids = mutableSetOf<String>()
            var lastDoc: com.google.firebase.firestore.DocumentSnapshot? = null
            var safetyHops = 0
            var docsRead = 0
            while (docsRead < MAX_BOOKMARK_READ && safetyHops < MAX_PAGE_HOPS) {
                val pageQuery = tweetsCol(uid)
                    .orderBy(FieldPath.documentId())
                    .let { q -> if (lastDoc != null) q.startAfter(lastDoc) else q }
                    .limit(READ_PAGE_SIZE.toLong())

                val snapshot = pageQuery.get().await()
                if (snapshot.isEmpty) break
                docsRead += snapshot.documents.size
                snapshot.documents.forEach { doc ->
                    // Skip quoted-tweet body docs (referenced=true). They live under
                    // tweets/ for the quoted sub-card but are NOT bookmarks — syncing
                    // them as top-level tweets would leak them into the feed.
                    if (doc.getBoolean("referenced") == true) return@forEach
                    doc.getString("tweetId")?.let(ids::add)
                }
                lastDoc = snapshot.documents.last()
                safetyHops++
                if (snapshot.documents.size < READ_PAGE_SIZE) break
            }
            Timber.d("Extracted ${ids.size} tweet IDs from $docsRead docs (page-hops=$safetyHops)")
            ids
        } catch (e: Exception) {
            Timber.e(e, "Error fetching tweet IDs from Firestore")
            emptySet()
        }
    }

    /**
     * Returns the net bookmark count from Firestore using three cheap aggregate `count()`
     * queries (one read operation each, no document data transferred):
     *  1. Total documents in the user's tweets sub-collection.
     *  2. Documents where `referenced == true` (quoted-tweet body docs, not bookmarks).
     *  3. Documents where `deleted == true` (soft-deleted via [markDeleted]'s swipe-right
     *     confirm-delete — the doc remains in Firestore as a tombstone).
     * Net = total - referenced - deleted — kept apples-to-apples with the local
     * `TweetDao.countAllActive()` semantics, which excludes referenced docs AND local
     * tombstones (the Room-side mirror of the same confirm-delete flow). Without the
     * `deleted` subtraction, any user who has ever confirmed a delete would see `total`
     * permanently inflated vs. the local active count, producing a false "incomplete
     * corpus" signal. NOTE: `referenced` and `deleted` are queried independently, so a doc
     * matching both would be double-subtracted; in practice this cannot happen — confirm-
     * delete only ever targets a surfaced bookmark id, never a quoted-tweet body doc — but
     * the residual risk is documented here rather than silently assumed away.
     * Each RPC is wrapped in its own [AGGREGATE_COUNT_TIMEOUT_MS] budget so a hung count
     * query can't block reconciliation indefinitely; the outer try/catch converts both a
     * timeout and any other failure to `Result.failure`, resetting the reconciliation gate
     * so the next cold-start can retry.
     */
    suspend fun getServerBookmarkCount(): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val uid = requireUid()
            val total = withTimeout(AGGREGATE_COUNT_TIMEOUT_MS) {
                tweetsCol(uid).count().get(AggregateSource.SERVER).await().count
            }
            val referenced = withTimeout(AGGREGATE_COUNT_TIMEOUT_MS) {
                tweetsCol(uid)
                    .whereEqualTo("referenced", true)
                    .count()
                    .get(AggregateSource.SERVER)
                    .await()
                    .count
            }
            val deleted = withTimeout(AGGREGATE_COUNT_TIMEOUT_MS) {
                tweetsCol(uid)
                    .whereEqualTo("deleted", true)
                    .count()
                    .get(AggregateSource.SERVER)
                    .await()
                    .count
            }
            val net = total - referenced - deleted
            Timber.tag("Reconcile").d("server_count total=$total referenced=$referenced deleted=$deleted net=$net")
            Result.success(net)
        } catch (e: Exception) {
            Timber.tag("Reconcile").w(e, "getServerBookmarkCount failed")
            Result.failure(e)
        }
    }

    /** A tail enumeration result: the docs read + whether the backfill floor was reached. */
    private data class TailEnumeration(val docs: List<CursorDoc>, val reachedFloor: Boolean)

    /**
     * PHASE A — incremental head. Walks `retrievedAt DESC` from the top, stopping at the first
     * doc whose `retrievedAt` is at/below [watermarkMillis] (descending order ⇒ everything after
     * is too). Catches every newly-seen item — including a newly-bookmarked OLD tweet (fresh
     * `retrievedAt`, old `createdAt`) a `createdAt`-only cursor would miss.
     *
     * `orderBy(retrievedAt)` SILENTLY EXCLUDES legacy NULL-`retrievedAt` docs — correct here:
     * those are never "new", they arrive only via the [enumerateBackfillTail] createdAt walk. A
     * null watermark (first incremental run) reads a single bounded SEED page just to establish
     * the watermark; the tail does the full-corpus heavy lifting on a cold start. The head needs
     * NO new composite index (single-field `retrievedAt` is auto-indexed). `get(Source.SERVER)`
     * pins server-authoritative ordering so a resumed cursor never reads off a stale cache.
     */
    private suspend fun enumerateIncrementalHead(watermarkMillis: Long?): List<CursorDoc> =
        withContext(Dispatchers.IO) {
            val uid = requireUid()
            try {
                val result = mutableListOf<CursorDoc>()
                var lastDoc: com.google.firebase.firestore.DocumentSnapshot? = null
                var safetyHops = 0
                var docsRead = 0
                var reachedWatermark = false
                while (!reachedWatermark && docsRead < MAX_BOOKMARK_READ && safetyHops < MAX_PAGE_HOPS) {
                    val pageQuery = tweetsCol(uid)
                        .orderBy("retrievedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .let { q -> if (lastDoc != null) q.startAfter(lastDoc) else q }
                        .limit(READ_PAGE_SIZE.toLong())

                    val snapshot = try {
                        withTimeout(ENUM_PAGE_TIMEOUT_MS) {
                            pageQuery.get(Source.SERVER).await()
                        }
                    } catch (e: TimeoutCancellationException) {
                        // A single stalled page must not unwind the whole phase and discard
                        // pages already read. Only propagate — preserving the existing
                        // fetchFailed signal — when NO page has succeeded yet this call
                        // (nothing to lose); otherwise break out with the partial results
                        // accumulated so far. The narrowed cursor still reflects what WAS
                        // read; the remainder is re-enumerated on the next run.
                        if (docsRead == 0) throw e
                        Timber.tag("IncrementalSync").w(
                            "head_page_timeout_partial docs_read=$docsRead kept=${result.size} hops=$safetyHops",
                        )
                        break
                    }
                    if (snapshot.isEmpty) break
                    docsRead += snapshot.documents.size
                    for (doc in snapshot.documents) {
                        // Skip quoted-tweet body docs (referenced=true) — hydrated as quoted
                        // sub-cards, never top-level bookmarks. Page PAST them regardless.
                        if (doc.getBoolean("referenced") == true) continue
                        val retrievedAtMillis = doc.getTimestamp("retrievedAt")?.toDate()?.time
                        if (watermarkMillis != null && retrievedAtMillis != null &&
                            retrievedAtMillis <= watermarkMillis
                        ) {
                            reachedWatermark = true
                            break
                        }
                        val id = doc.getString("tweetId") ?: continue
                        val createdAt = doc.getString("createdAt") ?: ""
                        result.add(CursorDoc(id, retrievedAtMillis, createdAt))
                    }
                    lastDoc = snapshot.documents.last()
                    safetyHops++
                    // Null watermark = seed mode: one bounded page is enough to set the watermark.
                    if (watermarkMillis == null) break
                    if (snapshot.documents.size < READ_PAGE_SIZE) break
                }
                Timber.tag("IncrementalSync")
                    .d("head_enumerated docs_read=$docsRead kept=${result.size} watermark=$watermarkMillis hops=$safetyHops")
                result
            } catch (e: Exception) {
                Timber.tag("IncrementalSync").e(e, "incremental_sync_failed reason=head_enumerate exception=${e.javaClass.simpleName}")
                throw e
            }
        }

    /**
     * PHASE B — backfill tail. Resumes the historical backfill via `createdAt DESC, __name__ ASC`
     * with a cross-run `startAfter(lowCreatedAt, lowTweetId)` FIELD-VALUE cursor (survives process
     * death, unlike a `DocumentSnapshot`) so an interrupted backfill continues from the last
     * committed position instead of re-walking the whole corpus. `createdAt` is present on EVERY
     * doc, so this phase (and only this phase) includes legacy NULL-`retrievedAt` bookmarks. The
     * tweet doc id IS its `tweetId`, so `__name__` is the same tiebreaker the [SyncCursor] stores.
     *
     * An empty/short page means the backfill reached the floor (complete); a safety-cap stop means
     * there is more to read next run. The existing `(createdAt DESC, __name__ ASC)` composite index
     * backs this unchanged. `get(Source.SERVER)` for the same stale-resume reason as the head.
     */
    private suspend fun enumerateBackfillTail(
        lowCreatedAt: String?,
        lowTweetId: String?,
    ): TailEnumeration = withContext(Dispatchers.IO) {
        val uid = requireUid()
        try {
            val result = mutableListOf<CursorDoc>()
            var lastDoc: com.google.firebase.firestore.DocumentSnapshot? = null
            var safetyHops = 0
            var docsRead = 0
            var reachedFloor = false
            while (docsRead < MAX_BOOKMARK_READ && safetyHops < MAX_PAGE_HOPS) {
                val base = tweetsCol(uid)
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .orderBy(FieldPath.documentId(), com.google.firebase.firestore.Query.Direction.ASCENDING)
                val pageQuery = when {
                    lastDoc != null -> base.startAfter(lastDoc)
                    lowCreatedAt != null && lowTweetId != null -> base.startAfter(lowCreatedAt, lowTweetId)
                    else -> base
                }.limit(READ_PAGE_SIZE.toLong())

                val snapshot = try {
                    withTimeout(ENUM_PAGE_TIMEOUT_MS) {
                        pageQuery.get(Source.SERVER).await()
                    }
                } catch (e: TimeoutCancellationException) {
                    // Same partial-results rationale as the head phase: a single stalled
                    // page must not discard pages already read this call. Propagate only
                    // when nothing has been read yet; otherwise break out with what WAS
                    // read. reachedFloor stays false — more remains for the next run, the
                    // same as hitting the MAX_PAGE_HOPS/MAX_BOOKMARK_READ safety caps below.
                    if (docsRead == 0) throw e
                    Timber.tag("IncrementalSync").w(
                        "tail_page_timeout_partial docs_read=$docsRead kept=${result.size} hops=$safetyHops",
                    )
                    break
                }
                if (snapshot.isEmpty) {
                    reachedFloor = true
                    break
                }
                docsRead += snapshot.documents.size
                for (doc in snapshot.documents) {
                    if (doc.getBoolean("referenced") == true) continue
                    val id = doc.getString("tweetId") ?: continue
                    val createdAt = doc.getString("createdAt") ?: ""
                    // null on legacy docs predating the serverTimestamp() field — kept (the head
                    // excludes them; the in-memory sort places them last, mirroring NULLs-last).
                    val retrievedAtMillis = doc.getTimestamp("retrievedAt")?.toDate()?.time
                    result.add(CursorDoc(id, retrievedAtMillis, createdAt))
                }
                lastDoc = snapshot.documents.last()
                safetyHops++
                if (snapshot.documents.size < READ_PAGE_SIZE) {
                    reachedFloor = true
                    break
                }
            }
            Timber.tag("IncrementalSync")
                .d("tail_enumerated docs_read=$docsRead kept=${result.size} resumeFrom=$lowCreatedAt reachedFloor=$reachedFloor hops=$safetyHops")
            TailEnumeration(result, reachedFloor)
        } catch (e: Exception) {
            Timber.tag("IncrementalSync").e(e, "incremental_sync_failed reason=tail_enumerate exception=${e.javaClass.simpleName}")
            throw e
        }
    }

    /**
     * Streaming, CURSOR-NARROWED variant of [fetchTweetsNotInLocal]. Resumes from [resumeFrom]
     * (the persisted [SyncCursor]) instead of re-enumerating the whole Firestore corpus each run:
     * a two-phase, key-split walk — incremental head ([enumerateIncrementalHead]) then backfill
     * tail ([enumerateBackfillTail]) — whose union is deduped against [localIds]/[deletedIds] and
     * planned by the pure [planNarrowedSync] into feed-ordered batches with phase-correct cursors.
     *
     * Each emission is a [SyncEmission]: the fetched aggregates plus the cursor the collector must
     * persist ATOMICALLY with the batch insert. Head batches stream first (newest `retrievedAt`,
     * so the feed head paints within seconds of sign-in); a final empty-entity TERMINAL emission
     * checkpoints the advanced watermark / backfill floor even on a nothing-new run. The local-IDs
     * diff is retained as the no-missed/no-duplicated backstop on top of the cursor narrowing.
     *
     * The `flow { … }` builder is cold by design — each `collect` re-runs the read, matching the
     * WorkManager `doWork()` contract (one collect per invocation). Do NOT add `.buffer()` or
     * `.flatMapMerge(...)` on the consumer side: cursor advancement assumes sequential per-batch
     * commits.
     */
    fun fetchTweetsNotInLocalStream(
        localIds: Set<String>,
        deletedIds: Set<String> = emptySet(),
        resumeFrom: SyncCursor = SyncCursor(),
    ): Flow<SyncEmission> = flow {
        val headDocs = enumerateIncrementalHead(resumeFrom.incrementalWatermarkMillis)
        val tail = enumerateBackfillTail(resumeFrom.lowCreatedAt, resumeFrom.lowTweetId)
        val plans = planNarrowedSync(
            headDocs = headDocs,
            tailDocs = tail.docs,
            prior = resumeFrom,
            localIds = localIds,
            deletedIds = deletedIds,
        )
        Timber.tag("IncrementalSync").d(
            "stream_start head_docs=${headDocs.size} tail_docs=${tail.docs.size} plans=${plans.size} localIds=${localIds.size} tail_floor=${tail.reachedFloor}",
        )
        plans.forEach { plan ->
            if (plan.ids.isEmpty()) {
                // TERMINAL checkpoint — no fetch; carry the cursor (advanced watermark / floor).
                emit(SyncEmission.Checkpoint(plan.cursor))
                return@forEach
            }
            // `batch_deser_ms` is the per-batch fetch + Firestore deserialization wall time — the
            // headline AC5 evidence (it falls once @IgnoreExtraProperties kills the warning storm).
            val startedNs = System.nanoTime()
            try {
                val entities = fetchTweetEntitiesByIds(plan.ids)
                val batchDeserMs = (System.nanoTime() - startedNs) / 1_000_000
                Timber.tag("IncrementalSync").d(
                    "batch_fetched phase=${plan.phase} requested=${plan.ids.size} returned=${entities.size} batch_deser_ms=$batchDeserMs",
                )
                emit(SyncEmission.Batch(entities, plan.cursor))
            } catch (e: TimeoutCancellationException) {
                // Outer BATCH_TIMEOUT_MS fired.  Log the dropped IDs so the gap is
                // visible in logcat, then emit Failed so the collector returns
                // Result.retry() WITHOUT advancing the cursor — these IDs will be
                // re-enumerated on the next run.
                Timber.tag("IncrementalSync").w(
                    "fetch_failed_batch ids=${plan.ids.take(5)} total=${plan.ids.size}; will retry",
                )
                emit(SyncEmission.Failed)
                return@forEach
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun fetchTweetsNotInLocal(localIds: Set<String>): List<TweetEntities> = withContext(Dispatchers.IO) {
        try {
            val firestoreIds = getAllTweetIds()
            val missingIds = firestoreIds - localIds

            if (missingIds.isEmpty()) {
                Timber.d("No missing tweets to fetch from Firestore")
                return@withContext emptyList()
            }

            // Firestore "in" query cap is 30.
            val batches = missingIds.chunked(30)
            val batchCount = batches.size
            Timber.d("Fetching ${missingIds.size} tweets from Firestore in $batchCount batch(es)")

            batches.mapIndexed { idx, batch ->
                val results = fetchTweetEntitiesByIds(batch)
                Timber.d("Synced batch ${idx + 1}/$batchCount: requested=${batch.size} returned=${results.size}")
                results
            }.flatten()
        } catch (e: Exception) {
            Timber.e(e, "Error fetching tweets from Firestore")
            emptyList()
        }
    }

    private suspend fun fetchTweetEntitiesByIds(tweetIds: List<String>): List<TweetEntities> = coroutineScope {
        if (tweetIds.isEmpty()) return@coroutineScope emptyList()
        val uid = requireUid()

        try {
            withTimeout(BATCH_TIMEOUT_MS) {
                val tweetsDeferred = async {
                    withTimeoutOrNull(SUB_FETCH_TIMEOUT_MS) {
                        tweetsCol(uid)
                            .whereIn("tweetId", tweetIds)
                            .get()
                            .await()
                            .toObjects(FirestoreTweet::class.java)
                            .associateBy { it.tweetId }
                    } ?: run {
                        Timber.w("tweetsCol sub-fetch timed out after ${SUB_FETCH_TIMEOUT_MS}ms for ids=${tweetIds.take(5)}")
                        emptyMap()
                    }
                }

                val usersDeferred = async {
                    withTimeoutOrNull(SUB_FETCH_TIMEOUT_MS) {
                        val tweets = tweetsDeferred.await()
                        val authorIds = tweets.values.map { it.authorId }.distinct()
                        if (authorIds.isEmpty()) return@withTimeoutOrNull emptyMap<String, FirestoreUser>()

                        authorIds.chunked(30).flatMap { batch ->
                            twitterUsersCol(uid)
                                .whereIn("userId", batch)
                                .get()
                                .await()
                                .toObjects(FirestoreUser::class.java)
                        }.associateBy { it.userId }
                    } ?: run {
                        Timber.w("twitterUsersCol sub-fetch timed out after ${SUB_FETCH_TIMEOUT_MS}ms for ids=${tweetIds.take(5)}")
                        emptyMap()
                    }
                }

                val metricsDeferred = async {
                    withTimeoutOrNull(SUB_FETCH_TIMEOUT_MS) {
                        metricsCol(uid)
                            .whereIn("tweetId", tweetIds)
                            .get()
                            .await()
                            .toObjects(FirestoreMetrics::class.java)
                            .associateBy { it.tweetId }
                    } ?: run {
                        Timber.w("metricsCol sub-fetch timed out after ${SUB_FETCH_TIMEOUT_MS}ms for ids=${tweetIds.take(5)}")
                        emptyMap()
                    }
                }

                val includesDeferred = async {
                    withTimeoutOrNull(SUB_FETCH_TIMEOUT_MS) {
                        includesCol(uid)
                            .whereIn("tweetId", tweetIds)
                            .get()
                            .await()
                            .toObjects(FirestoreIncludes::class.java)
                            .groupBy { it.tweetId }
                    } ?: run {
                        Timber.w("includesCol sub-fetch timed out after ${SUB_FETCH_TIMEOUT_MS}ms for ids=${tweetIds.take(5)}")
                        emptyMap()
                    }
                }

                // Quoted-tweet bodies: the ids referenced as type="quoted" by this
                // batch's tweets (from the includes _ref_ docs), fetched from the same
                // tweets/ collection. Mapped referenced=true so they hydrate the quoted
                // sub-card without ever surfacing as feed cards. A quoted id with no
                // doc here ⇒ the quote is unavailable (deleted/protected).
                val quotedTweetsDeferred = async {
                    withTimeoutOrNull(SUB_FETCH_TIMEOUT_MS) {
                        val includesByTweet = includesDeferred.await()
                        val quotedIds = includesByTweet.values.asSequence()
                            .flatten()
                            .filter { it.type == "quoted" && it.referencedTweetId != null }
                            .mapNotNull { it.referencedTweetId }
                            .distinct()
                            .toList()
                        if (quotedIds.isEmpty()) return@withTimeoutOrNull emptyMap<String, FirestoreTweet>()
                        quotedIds.chunked(30).flatMap { batch ->
                            tweetsCol(uid)
                                .whereIn("tweetId", batch)
                                .get()
                                .await()
                                .toObjects(FirestoreTweet::class.java)
                        }.associateBy { it.tweetId }
                    } ?: run {
                        Timber.w("quotedTweetsCol sub-fetch timed out after ${SUB_FETCH_TIMEOUT_MS}ms for ids=${tweetIds.take(5)}")
                        emptyMap()
                    }
                }

                // Authors of the quoted tweets — distinct from the bookmark authors and
                // fetched separately so the quoted sub-card's nested @Relation author
                // hydrates (still nullable-safe when an author doc is missing).
                val quotedAuthorsDeferred = async {
                    withTimeoutOrNull(SUB_FETCH_TIMEOUT_MS) {
                        val quoted = quotedTweetsDeferred.await()
                        val authorIds = quoted.values.map { it.authorId }.filter { it.isNotEmpty() }.distinct()
                        if (authorIds.isEmpty()) return@withTimeoutOrNull emptyMap<String, FirestoreUser>()
                        authorIds.chunked(30).flatMap { batch ->
                            twitterUsersCol(uid)
                                .whereIn("userId", batch)
                                .get()
                                .await()
                                .toObjects(FirestoreUser::class.java)
                        }.associateBy { it.userId }
                    } ?: run {
                        Timber.w("quotedAuthorsCol sub-fetch timed out after ${SUB_FETCH_TIMEOUT_MS}ms for ids=${tweetIds.take(5)}")
                        emptyMap()
                    }
                }

                // Media docs are keyed by `mediaKey` (the document id), not by
                // `tweetId` (no such field exists on the doc). The tweet→media
                // join lives in the includes collection: each includes doc that
                // represents a media attachment carries both `tweetId` and
                // `mediaKey`. So we await includes, collect the mediaKey set,
                // and fetch media by document id in chunks of 30.
                val mediaDeferred = async {
                    withTimeoutOrNull(SUB_FETCH_TIMEOUT_MS) {
                        val includesByTweet = includesDeferred.await()
                        val mediaKeys = includesByTweet.values.asSequence()
                            .flatten()
                            .mapNotNull { it.mediaKey }
                            .filter { it.isNotEmpty() }
                            .distinct()
                            .toList()
                        if (mediaKeys.isEmpty()) return@withTimeoutOrNull emptyMap<String, List<FirestoreMedia>>()

                        val mediaByKey = mediaKeys.chunked(30).flatMap { batch ->
                            mediaCol(uid)
                                .whereIn(FieldPath.documentId(), batch)
                                .get()
                                .await()
                                .toObjects(FirestoreMedia::class.java)
                        }.associateBy { it.documentId }

                        // Re-key from mediaKey → tweetId using the includes join.
                        includesByTweet.mapValues { (_, includesForTweet) ->
                            includesForTweet.mapNotNull { inc ->
                                inc.mediaKey?.takeIf { it.isNotEmpty() }?.let { mediaByKey[it] }
                            }
                        }
                    } ?: run {
                        Timber.w("mediaCol sub-fetch timed out after ${SUB_FETCH_TIMEOUT_MS}ms for ids=${tweetIds.take(5)}")
                        emptyMap()
                    }
                }

                val textAnnotationsDeferred = async {
                    withTimeoutOrNull(SUB_FETCH_TIMEOUT_MS) {
                        textAnnotationsCol(uid)
                            .whereIn("tweetId", tweetIds)
                            .get()
                            .await()
                            .toObjects(FirestoreTextAnnotation::class.java)
                            .groupBy { it.tweetId }
                    } ?: run {
                        Timber.w("textAnnotationsCol sub-fetch timed out after ${SUB_FETCH_TIMEOUT_MS}ms for ids=${tweetIds.take(5)}")
                        emptyMap()
                    }
                }

                val tweets = tweetsDeferred.await()
                val users = usersDeferred.await()
                val metrics = metricsDeferred.await()
                val includes = includesDeferred.await()
                val media = mediaDeferred.await()
                val textAnnotations = textAnnotationsDeferred.await()
                val quotedTweets = quotedTweetsDeferred.await()
                val quotedAuthors = quotedAuthorsDeferred.await()

                tweets.values.mapNotNull { firestoreTweet ->
                    assembleTweetEntities(
                        firestoreTweet = firestoreTweet,
                        users = users,
                        metrics = metrics,
                        includes = includes,
                        media = media,
                        textAnnotations = textAnnotations,
                        quotedTweets = quotedTweets,
                        quotedAuthors = quotedAuthors,
                    )
                }
            }
        } catch (e: CancellationException) {
            // Let TimeoutCancellationException and other structured-concurrency
            // cancellations propagate to fetchTweetsNotInLocalStream, which emits
            // fetchFailed=true so the worker returns Result.retry() without advancing
            // the cursor.  Any other CancellationException (network-lost constraint,
            // STOP_REASON_TIMEOUT) is also rethrown so structured concurrency is preserved.
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error fetching tweet entities by IDs")
            emptyList()
        }
    }

    /**
     * Public single-tweet wrapper over the private batch fetch. Re-pulls one
     * tweet's full entity set (tweet + author + metrics + media + text
     * annotations) from Firestore. Used by the lazy on-view media re-fetch and
     * the one-time backfill worker to repair the legacy (pre-cutover) corpus
     * whose media docs were never synced into Room. Returns null when the tweet
     * is absent or the batch times out.
     */
    suspend fun fetchSingleTweetEntities(tweetId: String): TweetEntities? =
        fetchTweetEntitiesByIds(listOf(tweetId)).firstOrNull()

    // The upload paths below become dead code after `cutover-migration` removes
    // the device-side X HTTP wiring. Path rewrites still land here so any
    // residual invocation continues to write under the user's sub-collections.
    suspend fun uploadTweet(tweetEntities: TweetEntities) = withContext(Dispatchers.IO) {
        try {
            val uid = requireUid()
            val tweetId = tweetEntities.tweetEntity.id

            val tweetRef = tweetsCol(uid).document(tweetId)
            val existingSnapshot = tweetRef.get().await()
            val isFirstWrite = !existingSnapshot.exists()
            if (!isFirstWrite) {
                Timber.d("Tweet already in Firestore — merging only")
            }

            val batch = db.batch()
            batch.set(tweetRef, FirestoreTweet.fromTweetEntity(tweetEntities.tweetEntity), SetOptions.merge())

            if (!isFirstWrite) {
                batch.commit().await()
                return@withContext
            }

            tweetEntities.twitterUserEntity.forEach { user ->
                val userRef = twitterUsersCol(uid).document()
                batch.set(userRef, FirestoreUser.fromTwitterUserEntity(user))
            }

            val metricsRef = metricsCol(uid).document()
            batch.set(metricsRef, FirestoreMetrics.fromTweetPublicMetrics(tweetEntities.tweetPublicMetrics))

            tweetEntities.tweetMediaEntity.forEach { media ->
                val mediaRef = mediaCol(uid).document()
                batch.set(mediaRef, FirestoreMedia.fromTweetMediaEntity(media))
            }

            tweetEntities.tweetIncludesEntity.forEach { include ->
                val includeRef = includesCol(uid).document()
                batch.set(includeRef, FirestoreIncludes.fromTweetIncludesEntity(include))
            }

            tweetEntities.tweetTextEntity.forEach { annotation ->
                val annotationRef = textAnnotationsCol(uid).document()
                batch.set(annotationRef, FirestoreTextAnnotation.fromTweetTextEntityAnnotation(annotation))
            }

            batch.commit().await()
            Timber.d("Successfully uploaded tweet to Firestore")
        } catch (e: Exception) {
            Timber.e(e, "Error uploading tweet to Firestore")
        }
    }

    suspend fun uploadTweets(tweets: List<TweetEntities>) = withContext(Dispatchers.IO) {
        tweets.chunked(BATCH_SIZE).forEach { batch ->
            batch.forEach { tweetEntities ->
                uploadTweet(tweetEntities)
            }
        }
    }

    /**
     * Swipe-right (confirm-delete) write. Stamps the user's per-item decision on
     * the server doc so the next daily poll skips it. `FieldValue.serverTimestamp()`
     * is monotonic and drift-free across devices; Firestore queues offline.
     */
    suspend fun markDeleted(tweetId: String): Unit = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: run {
            Timber.w("markDeleted called before authentication; tweetId=$tweetId")
            return@withContext
        }
        try {
            tweetsCol(uid).document(tweetId)
                .update(
                    mapOf(
                        "deleted" to true,
                        "deletedAt" to FieldValue.serverTimestamp(),
                    )
                )
                .await()
        } catch (e: Exception) {
            // Caller swallows; Room-side tombstone is the source of UI truth.
            Timber.w(e, "markDeleted: Firestore update failed for tweetId=$tweetId")
            throw e
        }
    }

    /**
     * Swipe-left (cancel-pending-delete) write. Clears the server-side flag so
     * the row returns to normal styling on the next poll.
     */
    suspend fun cancelPendingDelete(tweetId: String): Unit = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: run {
            Timber.w("cancelPendingDelete called before authentication; tweetId=$tweetId")
            return@withContext
        }
        try {
            tweetsCol(uid).document(tweetId)
                .update("pending_delete", false)
                .await()
        } catch (e: Exception) {
            Timber.w(e, "cancelPendingDelete: Firestore update failed for tweetId=$tweetId")
            throw e
        }
    }

    suspend fun syncLocalToFirestore(
        localTweets: List<TweetEntity>,
        getTweetEntitiesForId: suspend (String) -> TweetEntities?
    ) = withContext(Dispatchers.IO) {
        try {
            val firestoreIds = getAllTweetIds()
            val localIds = localTweets.map { it.id }.toSet()
            val missingInFirestore = localIds - firestoreIds

            if (missingInFirestore.isEmpty()) {
                Timber.d("All local tweets already exist in Firestore")
                return@withContext
            }

            Timber.d("Syncing ${missingInFirestore.size} local tweets to Firestore")

            missingInFirestore.forEach { tweetId ->
                val tweetEntities = getTweetEntitiesForId(tweetId)
                if (tweetEntities != null) {
                    uploadTweet(tweetEntities)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error syncing local tweets to Firestore")
        }
    }
}
