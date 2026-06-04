package com.github.jayteealao.twitter.data.firestore

import com.github.jayteealao.twitter.models.MediaKeys
import com.github.jayteealao.twitter.models.TweetEntities
import com.github.jayteealao.twitter.models.TweetEntity
import com.github.jayteealao.twitter.models.TweetReferencedTweets
import com.github.jayteealao.twitter.models.TweetReferencedTweetsFull
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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
        // Bumped from 30s → 120s. The 30s budget was getting blown on
        // mid-range Android devices when a batch returned ~30 metrics docs:
        // CustomClassMapper logs ~50 warnings per doc for unrecognized
        // snake_case overlay keys (like_count, retweet_count, etc.) the
        // server poll's pass-through writes — 1500+ logcat lines per batch
        // serialized on the IO thread, enough to stall deserialization past
        // 30s on a Samsung Galaxy class device. Either silence the warnings
        // OR widen the window; widening is the least-invasive while we ship.
        private const val BATCH_TIMEOUT_MS = 120_000L
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
     * Sibling of [getAllTweetIds] that lifts each doc's `createdAt` alongside the
     * tweet id, so the orchestrator can sort missing ids by `createdAt DESC`
     * before chunking — newest tweets land in Room first and the Twitter tab
     * paints within seconds of sign-in instead of waiting for the whole drain.
     *
     * Orders by `createdAt DESC, __name__ ASC` so the secondary order key
     * disambiguates docs sharing the same server timestamp (common when a
     * single poll batch lands 30 docs with identical `serverTimestamp()`
     * values). The DocumentSnapshot-based `startAfter(lastDoc)` is stable
     * across pages.
     */
    suspend fun getAllTweetIdsWithCreatedAt(): List<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            val uid = requireUid()
            try {
                val result = mutableListOf<Pair<String, String>>()
                var lastDoc: com.google.firebase.firestore.DocumentSnapshot? = null
                var safetyHops = 0
                var docsRead = 0
                while (docsRead < MAX_BOOKMARK_READ && safetyHops < MAX_PAGE_HOPS) {
                    val pageQuery = tweetsCol(uid)
                        .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .orderBy(FieldPath.documentId(), com.google.firebase.firestore.Query.Direction.ASCENDING)
                        .let { q -> if (lastDoc != null) q.startAfter(lastDoc) else q }
                        .limit(READ_PAGE_SIZE.toLong())

                    val snapshot = pageQuery.get().await()
                    if (snapshot.isEmpty) break
                    docsRead += snapshot.documents.size
                    snapshot.documents.forEach { doc ->
                        // Skip quoted-tweet body docs (referenced=true) — they are
                        // hydrated as quoted sub-cards, never as top-level bookmarks.
                        if (doc.getBoolean("referenced") == true) return@forEach
                        val id = doc.getString("tweetId") ?: return@forEach
                        val createdAt = doc.getString("createdAt") ?: ""
                        result.add(id to createdAt)
                    }
                    lastDoc = snapshot.documents.last()
                    safetyHops++
                    if (snapshot.documents.size < READ_PAGE_SIZE) break
                }
                Timber.d("getAllTweetIdsWithCreatedAt: ${result.size} ids in $docsRead docs (page-hops=$safetyHops)")
                result
            } catch (e: Exception) {
                Timber.tag("IncrementalSync").e(e, "incremental_sync_failed reason=fetch_ids exception=${e.javaClass.simpleName}")
                throw e
            }
        }

    /**
     * Streaming variant of [fetchTweetsNotInLocal]. Emits each batch's joined
     * `List<TweetEntities>` as soon as the per-batch parallel fan-out lands,
     * so a downstream collector (the WorkManager worker) can write to Room
     * incrementally — Room's `InvalidationTracker` then fires per batch and
     * the Paging source paints the newest tweets within seconds.
     *
     * Ordering: missing ids are sorted `createdAt DESC` before chunking so the
     * head-of-feed paints first. Same-millisecond ties are broken by tweet id
     * to keep the cursor encoding stable. Deleted-bookmark tombstones are
     * subtracted up front so we never spend a batch fetching a doc the user
     * already swiped away.
     *
     * The `flow { … }` builder is cold by design — each `collect` re-runs the
     * read, which matches the WorkManager `doWork()` contract (one collect
     * per invocation). Do NOT add `.buffer()` or `.flatMapMerge(...)` on the
     * consumer side: cursor advancement assumes sequential per-batch commits.
     */
    fun fetchTweetsNotInLocalStream(
        localIds: Set<String>,
        deletedIds: Set<String> = emptySet(),
    ): Flow<List<TweetEntities>> = flow {
        val allWithCreatedAt = getAllTweetIdsWithCreatedAt()
        val missing = allWithCreatedAt
            .filter { (id, _) -> id !in localIds && id !in deletedIds }
            .sortedWith(
                compareByDescending<Pair<String, String>> { it.second }
                    .thenBy { it.first }
            )
        if (missing.isEmpty()) {
            Timber.tag("IncrementalSync").d("stream_empty localIds=${localIds.size} firestoreIds=${allWithCreatedAt.size}")
            return@flow
        }
        val batches = missing.chunked(30)
        Timber.tag("IncrementalSync").d("stream_start total_missing=${missing.size} batches=${batches.size}")
        batches.forEachIndexed { idx, batch ->
            val ids = batch.map { it.first }
            val entities = fetchTweetEntitiesByIds(ids)
            Timber.tag("IncrementalSync")
                .d("batch_fetched batchIdx=$idx total=${batches.size} requested=${ids.size} returned=${entities.size}")
            emit(entities)
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
                    tweetsCol(uid)
                        .whereIn("tweetId", tweetIds)
                        .get()
                        .await()
                        .toObjects(FirestoreTweet::class.java)
                        .associateBy { it.tweetId }
                }

                val usersDeferred = async {
                    val tweets = tweetsDeferred.await()
                    val authorIds = tweets.values.map { it.authorId }.distinct()
                    if (authorIds.isEmpty()) return@async emptyMap()

                    authorIds.chunked(30).flatMap { batch ->
                        twitterUsersCol(uid)
                            .whereIn("userId", batch)
                            .get()
                            .await()
                            .toObjects(FirestoreUser::class.java)
                    }.associateBy { it.userId }
                }

                val metricsDeferred = async {
                    metricsCol(uid)
                        .whereIn("tweetId", tweetIds)
                        .get()
                        .await()
                        .toObjects(FirestoreMetrics::class.java)
                        .associateBy { it.tweetId }
                }

                val includesDeferred = async {
                    includesCol(uid)
                        .whereIn("tweetId", tweetIds)
                        .get()
                        .await()
                        .toObjects(FirestoreIncludes::class.java)
                        .groupBy { it.tweetId }
                }

                // Quoted-tweet bodies: the ids referenced as type="quoted" by this
                // batch's tweets (from the includes _ref_ docs), fetched from the same
                // tweets/ collection. Mapped referenced=true so they hydrate the quoted
                // sub-card without ever surfacing as feed cards. A quoted id with no
                // doc here ⇒ the quote is unavailable (deleted/protected).
                val quotedTweetsDeferred = async {
                    val includesByTweet = includesDeferred.await()
                    val quotedIds = includesByTweet.values.asSequence()
                        .flatten()
                        .filter { it.type == "quoted" && it.referencedTweetId != null }
                        .mapNotNull { it.referencedTweetId }
                        .distinct()
                        .toList()
                    if (quotedIds.isEmpty()) return@async emptyMap<String, FirestoreTweet>()
                    quotedIds.chunked(30).flatMap { batch ->
                        tweetsCol(uid)
                            .whereIn("tweetId", batch)
                            .get()
                            .await()
                            .toObjects(FirestoreTweet::class.java)
                    }.associateBy { it.tweetId }
                }

                // Authors of the quoted tweets — distinct from the bookmark authors and
                // fetched separately so the quoted sub-card's nested @Relation author
                // hydrates (still nullable-safe when an author doc is missing).
                val quotedAuthorsDeferred = async {
                    val quoted = quotedTweetsDeferred.await()
                    val authorIds = quoted.values.map { it.authorId }.filter { it.isNotEmpty() }.distinct()
                    if (authorIds.isEmpty()) return@async emptyMap<String, FirestoreUser>()
                    authorIds.chunked(30).flatMap { batch ->
                        twitterUsersCol(uid)
                            .whereIn("userId", batch)
                            .get()
                            .await()
                            .toObjects(FirestoreUser::class.java)
                    }.associateBy { it.userId }
                }

                // Media docs are keyed by `mediaKey` (the document id), not by
                // `tweetId` (no such field exists on the doc). The tweet→media
                // join lives in the includes collection: each includes doc that
                // represents a media attachment carries both `tweetId` and
                // `mediaKey`. So we await includes, collect the mediaKey set,
                // and fetch media by document id in chunks of 30.
                val mediaDeferred = async {
                    val includesByTweet = includesDeferred.await()
                    val mediaKeys = includesByTweet.values.asSequence()
                        .flatten()
                        .mapNotNull { it.mediaKey }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .toList()
                    if (mediaKeys.isEmpty()) return@async emptyMap<String, List<FirestoreMedia>>()

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
                }

                val textAnnotationsDeferred = async {
                    textAnnotationsCol(uid)
                        .whereIn("tweetId", tweetIds)
                        .get()
                        .await()
                        .toObjects(FirestoreTextAnnotation::class.java)
                        .groupBy { it.tweetId }
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
                    val tweetId = firestoreTweet.tweetId
                    val user = users[firestoreTweet.authorId] ?: return@mapNotNull null

                    // Restore the quoted-tweet relation on the FK-FREE junction. Each
                    // type="quoted" includes row → a reference row (+ the resolved quoted
                    // body, or null ⇒ unavailable). The DAO batch inserts the quoted
                    // TweetEntity (referenced=true, kept out of the feed) and the reference
                    // row together; with no FK on this path the original rollback cannot
                    // recur. tweetIncludesEntity stays empty — the dangerous mention/reply
                    // FK relation is intentionally NOT re-enabled.
                    val referencedFull = includes[tweetId].orEmpty()
                        .filter { it.type == "quoted" && it.referencedTweetId != null }
                        .distinctBy { it.referencedTweetId }
                        .map { inc ->
                            val refId = inc.referencedTweetId!!
                            TweetReferencedTweetsFull(
                                referencedTweets = TweetReferencedTweets(
                                    type = "quoted",
                                    id = refId,
                                    tweetId = tweetId,
                                ),
                                tweet = quotedTweets[refId]?.toTweetEntity(referenced = true),
                            )
                        }
                    val quotedAuthorEntities = referencedFull.mapNotNull { it.tweet }
                        .mapNotNull { quotedAuthors[it.authorId]?.toTwitterUserEntity() }
                    val authorEntities = (listOf(user.toTwitterUserEntity()) + quotedAuthorEntities)
                        .distinctBy { it.id }

                    TweetEntities(
                        tweetEntity = firestoreTweet.toTweetEntity(),
                        twitterUserEntity = authorEntities,
                        tweetPublicMetrics = metrics[tweetId]?.toTweetPublicMetrics()
                            ?: com.github.jayteealao.twitter.models.tweetPublicMetrics().copy(tweetId = tweetId),
                        tweetMediaEntity = media[tweetId]?.map { it.toTweetMediaEntity() } ?: emptyList(),
                        // Firestore-side `includes` rows carry mention/reply user ids that are
                        // not in this tweet's per-row TwitterUserEntity batch. Persisting them
                        // trips the TweetIncludesEntity → twitterUser / tweetEntity / tweetMedia
                        // foreign keys and rolls back the whole batch insert. The UI never reads
                        // TweetData.includes; the dangerous relation stays dropped. The quoted
                        // relation below rides the FK-free tweetReferencedTweets junction instead.
                        tweetIncludesEntity = emptyList(),
                        tweetReferencedTweets = referencedFull,
                        tweetContextAnnotationEntity = emptyList(),
                        tweetTextEntity = textAnnotations[tweetId]?.map { it.toTweetTextEntityAnnotation() } ?: emptyList(),
                        mediaKeys = media[tweetId]?.map { MediaKeys(tweetId, it.mediaKey) } ?: emptyList()
                    )
                }
            }
        } catch (e: TimeoutCancellationException) {
            // Per-batch timeout fired — log and return empty so the next batch
            // can proceed. Structural cancellation (parent scope cancelling) is
            // handled by the next catch arm.
            Timber.w("Batch timed out after ${BATCH_TIMEOUT_MS}ms, returning empty for ${tweetIds.size} IDs")
            emptyList()
        } catch (e: CancellationException) {
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
