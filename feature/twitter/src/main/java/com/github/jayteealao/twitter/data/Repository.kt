package com.github.jayteealao.twitter.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.data.DeletedBookmarkRepository
import com.github.jayteealao.crumbs.data.FilterState
import com.github.jayteealao.crumbs.data.SyncProgress
import com.github.jayteealao.crumbs.data.SyncProgressDao
import com.github.jayteealao.crumbs.data.TagRepository
import com.github.jayteealao.twitter.data.firestore.FirestoreRepository
import com.github.jayteealao.twitter.models.TagEntity
import com.github.jayteealao.twitter.models.TweetData
import com.github.jayteealao.twitter.models.TweetEntities
import com.github.jayteealao.twitter.models.TweetEntity
import com.github.jayteealao.twitter.models.TweetTagCrossRef
import com.github.jayteealao.crumbs.data.di.ApplicationScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Repository @Inject constructor(
    private val tweetDao: TweetDao,
    private val authPref: Prefs,
    private val firestoreRepository: FirestoreRepository,
    private val deletedBookmarkRepository: DeletedBookmarkRepository,
    private val callableService: TwitterCallableService,
    @ApplicationScope private val scope: CoroutineScope,
    private val syncEnqueuer: TwitterSyncEnqueuer,
    private val syncProgressDao: SyncProgressDao,
    private val auth: FirebaseAuth,
) : TagRepository {
    private var latestBookmarkInDatabase: TweetEntity? = null
    private var orderOfLastBookmark: Int = 1000
    private val fetchMutex = Mutex()

    private val _isRefreshing = MutableStateFlow(false)
    /**
     * Combines the brief in-flight `triggerPoll` flag with the long-running
     * WorkManager state for the unique sync worker. The UI spinner therefore
     * stays up across backgrounding, recreation, and process death — driven
     * by `WorkInfo.State.{ENQUEUED, RUNNING}` for the duration of the worker
     * + the local `_isRefreshing` window while we wait on `triggerPoll`.
     */
    val isRefreshing: StateFlow<Boolean> = combine(
        _isRefreshing.asStateFlow(),
        syncEnqueuer.observeIsRunning(),
    ) { triggering, syncing -> triggering || syncing }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    private val _snackbarEvents = MutableSharedFlow<TwitterSnackbarEvent>(replay = 0, extraBufferCapacity = 4)
    val snackbarEvents: SharedFlow<TwitterSnackbarEvent> = _snackbarEvents.asSharedFlow()

    companion object {
        const val BUFFER = 250
        // Cap the size of any `IN (:ids)` list so a large paging snapshot cannot
        // exceed SQLite's default 999 host-parameter limit. 900 leaves headroom
        // for any other bound parameters in the same statement.
        const val MAX_IN_CLAUSE_PARAMS = 900
        // Absorbs concurrent in-flight deletes (tombstone written to Room before Firestore;
        // 1–2 events typical) plus the Firestore→Room write-race window during the count
        // queries. Chosen well below any real shortfall (690+ on a partial corpus) while
        // generous enough to never false-positive on a healthy corpus.
        // sdlc-debt: fixed constant; could be a DataStore-backed tunable if the tolerance
        // needs per-user calibration. Upgrade: replace with a persisted preference.
        private const val RECONCILIATION_TOLERANCE = 5L
        // Backstop for the auto-clear watcher (see reconcileIfIncomplete): bounds how long
        // the watcher will suspend waiting for the local count to catch up to the server
        // threshold before giving up. Generous relative to a healthy refill (which should
        // complete in seconds to low minutes) so it never races a real in-flight sync, but
        // finite so a stalled/failed refill cannot leave "CATCHING UP" shown forever.
        private const val RECONCILE_WATCH_TIMEOUT_MS = 5 * 60_000L
    }

    // Per-uid one-shot gate: Set.add(uid) returns true on the first (real) call for that
    // uid this process lifetime; subsequent calls for the SAME uid see false and return
    // immediately without issuing a Firestore read. Keyed by uid (not a single process-wide
    // flag) so an in-process account switch (sign-out/sign-in) re-triggers reconciliation
    // for the newly-signed-in uid instead of being silently defeated by the previous
    // account's check. Removed from the set on a transient count-query failure so the next
    // cold-start for that uid can retry. ConcurrentHashMap.newKeySet is thread-safe for
    // concurrent add/remove from multiple callers.
    private val reconciledUids = ConcurrentHashMap.newKeySet<String>()

    private val _isSyncIncomplete = MutableStateFlow(false)
    /**
     * True while reconciliation detected local < server (beyond tolerance) and the
     * fill sync is in flight. Auto-clears reactively when the live Room count crosses
     * the server threshold. Drives the "CATCHING UP" count-header affordance in the UI.
     */
    val isSyncIncomplete: StateFlow<Boolean> = _isSyncIncomplete.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            try {
                latestBookmarkInDatabase = tweetDao.getLatestBookmark()
                Timber.d("latest bookmark in database: $latestBookmarkInDatabase")
                if (latestBookmarkInDatabase != null) {
                    orderOfLastBookmark = latestBookmarkInDatabase!!.order
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in Repository init")
            }
        }
    }

    fun saveTweetEntities(tweetEntities: TweetEntities, uploadToFirestore: Boolean = true) {
        tweetDao.insertTweetEntitiesAtomic(
            tweetEntities.tweetEntity,
            tweetEntities.tweetReferencedTweets.mapNotNull { it.tweet },
            tweetEntities.twitterUserEntity,
            tweetEntities.tweetPublicMetrics,
            tweetEntities.tweetMediaEntity,
            tweetEntities.tweetIncludesEntity,
            tweetEntities.tweetReferencedTweets.map { it.referencedTweets },
            tweetEntities.tweetContextAnnotationEntity,
            tweetEntities.tweetTextEntity,
            tweetEntities.mediaKeys,
            tweetEntities.pollIds,
        )
        // Also upload to Firestore for backup
        if (uploadToFirestore) {
            scope.launch(Dispatchers.IO) {
                firestoreRepository.uploadTweet(tweetEntities)
            }
        }
    }

    /**
     * Single-tweet media re-fetch for the legacy (pre-cutover) corpus. Re-pulls the
     * tweet's full entity set from Firestore and writes it through the existing
     * IGNORE-on-conflict atomic insert: the already-present tweet/user/metrics rows
     * are no-ops, so only the missing media (+ text annotations + media keys) land.
     * Idempotent — safe to call repeatedly and from the backfill worker. Returns
     * true when the re-fetched tweet carried any media rows.
     *
     * Drives the card's retry-on-revisit: on a successful media insert Room's
     * InvalidationTracker re-emits the paged card with its images. Does NOT re-upload
     * to Firestore and does NOT touch the includes-drop block (quoted-tweet FKs are
     * owned by the quoted-tweets slice).
     *
     * Also repairs legacy video rows: the IGNORE-on-conflict aggregate insert adds any
     * MISSING media but never overwrites an existing row, so a present-but-variant-less
     * video row would keep its NULL `video_variants`. The explicit per-row update lands
     * the freshly-fetched variants onto those existing rows (inline video's legacy repair).
     */
    suspend fun refetchTweetMedia(tweetId: String): Boolean = withContext(Dispatchers.IO) {
        val entities = firestoreRepository.fetchSingleTweetEntities(tweetId)
            ?: return@withContext false
        if (entities.tweetMediaEntity.isEmpty()) return@withContext false
        saveTweetEntities(entities, uploadToFirestore = false)
        entities.tweetMediaEntity
            .filter { !it.videoVariants.isNullOrEmpty() }
            .forEach { tweetDao.updateMedia(it) }
        true
    }

    /**
     * Single-tweet link re-fetch for the legacy corpus. Re-pulls the tweet's
     * url-entity rows from Firestore (where the server-side link-enrichment
     * function writes them) and replaces the local set via the duplicate-safe
     * [TweetDao.replaceUrlAnnotations] — the aggregate IGNORE insert never lands
     * url rows for a media-less tweet (it early-returns) and would duplicate them
     * for one that has media, so links need their own path. Idempotent; on a
     * successful insert Room's InvalidationTracker re-emits the card as a Link
     * with its preview. Returns true when an EXTERNAL link row landed.
     */
    suspend fun refetchTweetLinks(tweetId: String): Boolean = withContext(Dispatchers.IO) {
        val entities = firestoreRepository.fetchSingleTweetEntities(tweetId)
            ?: return@withContext false
        val urlRows = entities.tweetTextEntity.filter { it.type == "urls" }
        if (urlRows.isEmpty()) return@withContext false
        tweetDao.replaceUrlAnnotations(tweetId, urlRows)
        urlRows.any { row ->
            val expanded = row.expandedUrl
            expanded != null && !expanded.contains("twitter.com") && !expanded.contains("x.com")
        }
    }

    /**
     * Single-tweet quoted-body re-fetch for the legacy corpus. Re-pulls the tweet's
     * full entity set from Firestore — which now resolves the quoted body (+ author)
     * and rebuilds the FK-free `tweetReferencedTweets` rows once the server has written
     * the quoted doc — then writes it through the IGNORE-on-conflict atomic insert: the
     * parent + existing reference rows are no-ops, so only the missing quoted
     * [com.github.jayteealao.twitter.models.TweetEntity] (+ its author) lands. The
     * existing reference row's @Relation junction then resolves and Room's
     * InvalidationTracker re-emits the card with the rendered quote. Returns true when
     * the re-fetch carried a resolved quoted body.
     */
    suspend fun refetchTweetQuotes(tweetId: String): Boolean = withContext(Dispatchers.IO) {
        val entities = firestoreRepository.fetchSingleTweetEntities(tweetId)
            ?: return@withContext false
        if (entities.tweetReferencedTweets.none { it.tweet != null }) return@withContext false
        saveTweetEntities(entities, uploadToFirestore = false)
        true
    }

    /**
     * Cold-start completeness check. Runs at most once per signed-in uid per process
     * lifetime (the [reconciledUids] gate, keyed by uid so an in-process account switch
     * re-triggers the check for the new account) to avoid repeated Firestore reads.
     *
     * Compares the Firestore aggregate bookmark count against the local Room count.
     * When the gap exceeds [RECONCILIATION_TOLERANCE], REWINDS the persisted [SyncProgress]
     * cursor for this uid before re-kicking a cold-start sync (see below for why), and
     * activates [isSyncIncomplete] to surface the "CATCHING UP" header signal. The signal
     * auto-clears reactively once the Room count reaches the server threshold — no explicit
     * "sync complete" callback required — bounded by [RECONCILE_WATCH_TIMEOUT_MS] so a
     * stalled refill cannot leave the signal stuck forever.
     *
     * On a count-query failure the gate is reset so the next cold-start can retry.
     * Returns true if a reconcile-triggered sync was kicked; false in all other cases.
     */
    suspend fun reconcileIfIncomplete(): Boolean = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: run {
            Timber.tag("Reconcile").d("reconcile_skipped reason=not_authenticated")
            return@withContext false
        }
        if (!reconciledUids.add(uid)) {
            Timber.tag("Reconcile").d("reconcile_skipped reason=already_checked_this_session uid=$uid")
            return@withContext false
        }
        val serverTotal = firestoreRepository.getServerBookmarkCount().getOrElse { e ->
            Timber.tag("Reconcile").w(e, "reconcile_aborted reason=count_query_failed")
            reconciledUids.remove(uid) // allow retry on next cold-start
            return@withContext false
        }
        val localCount = tweetDao.countAllActive().toLong()
        val gap = serverTotal - localCount
        Timber.tag("Reconcile").d(
            "reconcile_check server=$serverTotal local=$localCount gap=$gap tolerance=$RECONCILIATION_TOLERANCE"
        )
        if (gap > RECONCILIATION_TOLERANCE) {
            _isSyncIncomplete.value = true
            // Rewind the persisted cursor BEFORE kicking the refill. A corpus that already
            // finished backfilling (tail floor reached, head watermark advanced) but then
            // lost local rows would otherwise re-run TwitterSyncWorker against a cursor that
            // considers the whole range already covered — a structural no-op that re-fetches
            // nothing. Nulling every cursor field (and the batch counter) puts the uid back
            // in "fresh install" state so the worker fully re-enumerates from scratch. This is
            // safe: every write in the aggregate insert path is IGNORE-on-conflict (see
            // saveTweetEntities / insertTweetEntitiesAtomic), so rows the device already has
            // are no-ops on re-fetch and only the genuinely missing rows land — no duplication.
            // Scoped to this reconcile-kick path only; the normal (non-reconcile) cold-start /
            // refresh cursor-resume path never calls this and is unaffected.
            syncProgressDao.upsert(
                SyncProgress(
                    uid = uid,
                    lastHighCursorCreatedAt = null,
                    lastHighCursorTweetId = null,
                    lastLowCursorCreatedAt = null,
                    lastLowCursorTweetId = null,
                    totalBatchesIngested = 0,
                    lastUpdatedAtMs = System.currentTimeMillis(),
                    lastIncrementalRetrievedAtMs = null,
                )
            )
            syncEnqueuer.enqueueColdStart()
            Timber.tag("Reconcile").d("reconcile_kick_sync gap=$gap exceeds_tolerance cursor_rewound=true")
            // Auto-clear: suspend until Room's live count crosses the server threshold,
            // then flip the signal off. Flow.first { } cancels itself after the predicate
            // fires. Runs on the application scope (not the caller's) so the watcher
            // outlives the LaunchedEffect that triggered checkAndReconcile.
            // Bounded by RECONCILE_WATCH_TIMEOUT_MS as a backstop: if the server deletes
            // docs between the count query and the sync, or the refill stalls/fails, the
            // local count may never reach `target`. Without a timeout the watcher — and the
            // "CATCHING UP" header — would live until the app process dies. On timeout we
            // still clear the signal (rather than leaking the coroutine indefinitely) and
            // log a distinct reason so a stuck refill is diagnosable from logs.
            val target = serverTotal - RECONCILIATION_TOLERANCE
            scope.launch(Dispatchers.IO) {
                val reachedTarget = withTimeoutOrNull(RECONCILE_WATCH_TIMEOUT_MS) {
                    tweetDao.countTombstoneAware("ALL").first { it.toLong() >= target }
                }
                _isSyncIncomplete.value = false
                if (reachedTarget == null) {
                    Timber.tag("Reconcile").w(
                        "reconcile_watch_timeout target=$target timeout_ms=$RECONCILE_WATCH_TIMEOUT_MS"
                    )
                } else {
                    Timber.tag("Reconcile").d("reconcile_cleared local_count_reached=$target")
                }
            }
            true
        } else {
            _isSyncIncomplete.value = false
            Timber.tag("Reconcile").d("reconcile_noop gap=$gap within_tolerance")
            false
        }
    }

    /**
     * Pull-to-refresh entry point. Calls the server-side `triggerPoll` callable
     * to wake the daily-poll function, then enqueues the local
     * `TwitterSyncWorker` so any newly-arrived Firestore docs are streamed
     * into Room. The worker is enqueued unique-by-uid with `KEEP` policy so
     * back-to-back pull gestures coalesce instead of stacking.
     *
     * The `isRefreshing` state surfaced to the UI is driven by the worker's
     * `WorkInfo` (see `BookmarksViewModel`) so it survives backgrounding and
     * activity recreation. This method's `_isRefreshing` flag only covers the
     * brief `triggerPoll` callable window; once the callable returns and the
     * worker is enqueued, the WorkInfo flow takes over.
     *
     * `triggerPoll` failures still flow through `_snackbarEvents` so the UI
     * can surface "debounced" / "in progress" / generic-failure copy.
     */
    suspend fun refreshBookmarks() {
        if (!fetchMutex.tryLock()) {
            Timber.d("refreshBookmarks: another refresh in flight, skipping")
            return
        }
        _isRefreshing.value = true
        try {
            val result = runCatching { callableService.triggerPoll() }
            // Always enqueue the local sync, even if triggerPoll failed — the
            // prior poll (e.g., the oauthCallback fan-out) may have written
            // docs the device hasn't synced locally yet. This is the recovery
            // path when cold-start enqueue raced Firebase Auth restoration.
            syncEnqueuer.enqueueRefresh()

            val payload = result.getOrNull()
            if (payload == null) {
                Timber.w("triggerPoll returned no payload; assuming failure")
                _snackbarEvents.tryEmit(
                    TwitterSnackbarEvent.GenericFailure(result.exceptionOrNull()?.message ?: "no_response")
                )
                return
            }

            val ok = payload["ok"] as? Boolean ?: false
            if (!ok) {
                val reason = payload["reason"] as? String
                val event = when (reason) {
                    // retryAfter is only populated for the debounced result, so it is
                    // read inside that branch rather than unconditionally.
                    "debounced" -> TwitterSnackbarEvent.Debounced((payload["retryAfter"] as? Number)?.toInt())
                    "in_progress" -> TwitterSnackbarEvent.InProgress
                    else -> TwitterSnackbarEvent.GenericFailure(reason ?: "unknown")
                }
                _snackbarEvents.tryEmit(event)
            }
        } finally {
            _isRefreshing.value = false
            fetchMutex.unlock()
        }
    }

    /**
     * Server-side disconnect. Deletes the Secret Manager refresh token and
     * flips sync_status.linked=false; on success the local Prefs are cleared
     * so the device never carries the X credential again.
     */
    suspend fun disconnectX(): Result<Unit> = runCatching {
        callableService.disconnectX()
        authPref.clearAllTokens()
        Timber.d("Twitter tokens cleared after server-side disconnect")
    }

    private val pager = Pager(
        config = PagingConfig(
            pageSize = 20
        )
    ) {
        tweetDao.getTweets()
    }

    fun pagingTweetData() = pager.flow

    fun pagingTweetData(filter: FilterState): Flow<PagingData<TweetData>> {
        val type = filter.type.name
        val pagingSource = if (filter.selectedTags.isNotEmpty()) {
            { tweetDao.getTweetsByTagsTombstoneAware(filter.selectedTags.toList(), type) }
        } else {
            { tweetDao.getTweetsTombstoneAware(type) }
        }
        return Pager(
            config = PagingConfig(pageSize = 20),
            pagingSourceFactory = pagingSource,
        ).flow
    }

    /**
     * Reactive count of the feed the SAVED header reports. Built from the **same**
     * [FilterState] tags/no-tags branch and the **same** `:type` predicate as
     * [pagingTweetData], so the header tracks the visible list exactly. `distinctUntilChanged`
     * collapses no-op re-emissions from unrelated table churn during sync drains.
     */
    fun countFlow(filter: FilterState): Flow<Int> {
        val type = filter.type.name
        val source = if (filter.selectedTags.isNotEmpty()) {
            tweetDao.countByTagsTombstoneAware(filter.selectedTags.toList(), type)
        } else {
            tweetDao.countTombstoneAware(type)
        }
        return source.distinctUntilChanged()
    }

    suspend fun softDelete(id: String) {
        deletedBookmarkRepository.softDelete(id, BookmarkSource.Twitter)
    }

    suspend fun undoDelete(id: String) {
        deletedBookmarkRepository.undoDelete(id, BookmarkSource.Twitter)
    }

    /**
     * Swipe-right confirm: persist a Room tombstone (so the row disappears from
     * the paging Flow immediately) then stamp the server doc as deleted. The
     * Firestore write is best-effort offline — Room is the source of UI truth.
     */
    suspend fun confirmDeletePending(id: String) {
        deletedBookmarkRepository.softDelete(id, BookmarkSource.Twitter)
        runCatching { firestoreRepository.markDeleted(id) }
            .onFailure { Timber.w(it, "confirmDeletePending: Firestore mark failed for id=$id") }
    }

    /**
     * Swipe-left cancel: clear the local `pending_delete` flag first so the
     * card flips back to normal styling immediately, then sync to Firestore.
     */
    suspend fun cancelDeletePending(id: String) {
        tweetDao.updatePendingDelete(id, false)
        runCatching { firestoreRepository.cancelPendingDelete(id) }
            .onFailure { Timber.w(it, "cancelDeletePending: Firestore clear failed for id=$id") }
    }

    // Tag operations
    override suspend fun addTagToTweet(tweetId: String, tagName: String) {
        // Insert the tag if it doesn't exist
        tweetDao.insertTag(TagEntity(tagName))
        // Link the tag to the tweet
        tweetDao.insertTweetTag(TweetTagCrossRef(tweetId, tagName))
    }

    override suspend fun removeTagFromTweet(tweetId: String, tagName: String) {
        tweetDao.deleteTweetTag(tweetId, tagName)
    }

    override suspend fun getTagsForTweet(tweetId: String): List<String> {
        return tweetDao.getTagsForTweet(tweetId)
    }

    override suspend fun getTagsForItems(ids: List<String>): Map<String, List<String>> {
        if (ids.isEmpty()) return emptyMap()
        // Chunk the IN-clause to stay under SQLite's host-parameter limit, then
        // merge the per-chunk rows. Each id lands in exactly one chunk, so a flat
        // overwrite-merge cannot drop or double-count tags.
        val tagsById = mutableMapOf<String, List<String>>()
        ids.chunked(MAX_IN_CLAUSE_PARAMS).forEach { chunk ->
            tweetDao.getTagsForTweets(chunk)
                .groupBy({ it.tweetId }, { it.tagName })
                .forEach { (id, tags) -> tagsById[id] = tags }
        }
        // Inject an explicit empty entry for every requested id with no tags so the
        // ViewModel's overwrite-merge clears chips for ids whose tags were removed.
        ids.forEach { id -> tagsById.getOrPut(id) { emptyList() } }
        return tagsById
    }

    override suspend fun getAllTags(): List<String> {
        return tweetDao.getAllTags().map { it.name }
    }

    override suspend fun saveTags(tweetId: String, tags: List<String>) {
        tweetDao.saveTagsAtomic(tweetId, tags)
    }

    /**
     * Clear all Twitter tokens to force re-authentication.
     */
    suspend fun logout() {
        authPref.clearAllTokens()
        Timber.d("Twitter tokens cleared")
    }
}
