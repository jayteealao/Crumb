package com.github.jayteealao.twitter.data.firestore

import com.github.jayteealao.twitter.models.MediaKeys
import com.github.jayteealao.twitter.models.TweetEntities
import com.github.jayteealao.twitter.models.TweetEntity
import com.github.jayteealao.twitter.models.TweetReferencedTweetsFull
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreRepository @Inject constructor() {
    private val db: FirebaseFirestore = Firebase.firestore

    companion object {
        private const val TWEETS_COLLECTION = "tweets"
        private const val USERS_COLLECTION = "users"
        private const val MEDIA_COLLECTION = "media"
        private const val METRICS_COLLECTION = "metrics"
        private const val INCLUDES_COLLECTION = "includes"
        private const val TEXT_ANNOTATIONS_COLLECTION = "textAnnotations"
        private const val BATCH_SIZE = 500
        // Hard ceiling on tweets read from Firestore per backfill. Keeps a
        // pathological account from blowing past Firestore's free-tier
        // 50k reads/day and protects against malicious upload abuse.
        private const val MAX_BOOKMARK_READ = 10_000
        private const val READ_PAGE_SIZE = 500
        // Belt-and-suspenders bound on pagination loop iterations.
        private const val MAX_PAGE_HOPS = 50
    }

    /**
     * Fetch all tweet IDs from Firestore.
     *
     * Pages through the collection with a hard cap of [MAX_BOOKMARK_READ] so a
     * runaway account (or a malicious push of millions of stub docs) cannot
     * force the client to download — and pay for — an unbounded set of reads.
     * Read costs are proportional to documents returned; the page cursor stops
     * cleanly when we have either exhausted the collection or hit the cap.
     */
    suspend fun getAllTweetIds(): Set<String> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Fetching tweet IDs from Firestore (max=$MAX_BOOKMARK_READ)")
            val ids = mutableSetOf<String>()
            var lastDoc: com.google.firebase.firestore.DocumentSnapshot? = null
            var safetyHops = 0
            var docsRead = 0
            // Cap on *documents read* (billable reads), not on ids collected —
            // a stream of docs without `tweetId` would otherwise let the loop
            // walk MAX_PAGE_HOPS * READ_PAGE_SIZE billable reads before exit.
            while (docsRead < MAX_BOOKMARK_READ && safetyHops < MAX_PAGE_HOPS) {
                val pageQuery = db.collection(TWEETS_COLLECTION)
                    .orderBy(com.google.firebase.firestore.FieldPath.documentId())
                    .let { q -> if (lastDoc != null) q.startAfter(lastDoc) else q }
                    .limit(READ_PAGE_SIZE.toLong())

                val snapshot = pageQuery.get().await()
                if (snapshot.isEmpty) break
                docsRead += snapshot.documents.size
                snapshot.documents.forEach { doc ->
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
     * Fetch tweets from Firestore that are not in the local database
     * @param localIds Set of tweet IDs already in local database
     * @return List of TweetEntities to be inserted into local database
     */
    suspend fun fetchTweetsNotInLocal(localIds: Set<String>): List<TweetEntities> = withContext(Dispatchers.IO) {
        try {
            val firestoreIds = getAllTweetIds()
            val missingIds = firestoreIds - localIds

            if (missingIds.isEmpty()) {
                Timber.d("No missing tweets to fetch from Firestore")
                return@withContext emptyList()
            }

            Timber.d("Fetching ${missingIds.size} tweets from Firestore")

            // Fetch in batches due to Firestore "in" query limit of 30
            missingIds.chunked(30).flatMap { batch ->
                fetchTweetEntitiesByIds(batch)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching tweets from Firestore")
            emptyList()
        }
    }

    /**
     * Fetch complete TweetEntities for a batch of tweet IDs
     */
    private suspend fun fetchTweetEntitiesByIds(tweetIds: List<String>): List<TweetEntities> = coroutineScope {
        if (tweetIds.isEmpty()) return@coroutineScope emptyList()

        try {
            // Fetch tweets
            val tweetsDeferred = async {
                db.collection(TWEETS_COLLECTION)
                    .whereIn("tweetId", tweetIds)
                    .get()
                    .await()
                    .toObjects(FirestoreTweet::class.java)
                    .associateBy { it.tweetId }
            }

            // Fetch users for these tweets
            val usersDeferred = async {
                val tweets = tweetsDeferred.await()
                val authorIds = tweets.values.map { it.authorId }.distinct()
                if (authorIds.isEmpty()) return@async emptyMap()

                authorIds.chunked(30).flatMap { batch ->
                    db.collection(USERS_COLLECTION)
                        .whereIn("userId", batch)
                        .get()
                        .await()
                        .toObjects(FirestoreUser::class.java)
                }.associateBy { it.userId }
            }

            // Fetch metrics
            val metricsDeferred = async {
                db.collection(METRICS_COLLECTION)
                    .whereIn("tweetId", tweetIds)
                    .get()
                    .await()
                    .toObjects(FirestoreMetrics::class.java)
                    .associateBy { it.tweetId }
            }

            // Fetch media
            val mediaDeferred = async {
                db.collection(MEDIA_COLLECTION)
                    .whereIn("tweetId", tweetIds)
                    .get()
                    .await()
                    .toObjects(FirestoreMedia::class.java)
                    .groupBy { it.tweetId ?: "" }
            }

            // Fetch includes
            val includesDeferred = async {
                db.collection(INCLUDES_COLLECTION)
                    .whereIn("tweetId", tweetIds)
                    .get()
                    .await()
                    .toObjects(FirestoreIncludes::class.java)
                    .groupBy { it.tweetId }
            }

            // Fetch text annotations
            val textAnnotationsDeferred = async {
                db.collection(TEXT_ANNOTATIONS_COLLECTION)
                    .whereIn("tweetId", tweetIds)
                    .get()
                    .await()
                    .toObjects(FirestoreTextAnnotation::class.java)
                    .groupBy { it.tweetId }
            }

            // Await all
            val tweets = tweetsDeferred.await()
            val users = usersDeferred.await()
            val metrics = metricsDeferred.await()
            val media = mediaDeferred.await()
            val includes = includesDeferred.await()
            val textAnnotations = textAnnotationsDeferred.await()

            // Build TweetEntities
            tweets.values.mapNotNull { firestoreTweet ->
                val tweetId = firestoreTweet.tweetId
                val user = users[firestoreTweet.authorId] ?: return@mapNotNull null

                TweetEntities(
                    tweetEntity = firestoreTweet.toTweetEntity(),
                    twitterUserEntity = listOf(user.toTwitterUserEntity()),
                    tweetPublicMetrics = metrics[tweetId]?.toTweetPublicMetrics()
                        ?: com.github.jayteealao.twitter.models.tweetPublicMetrics().copy(tweetId = tweetId),
                    tweetMediaEntity = media[tweetId]?.map { it.toTweetMediaEntity() } ?: emptyList(),
                    tweetIncludesEntity = includes[tweetId]?.map { it.toTweetIncludesEntity() } ?: emptyList(),
                    tweetReferencedTweets = emptyList(),
                    tweetContextAnnotationEntity = emptyList(),
                    tweetTextEntity = textAnnotations[tweetId]?.map { it.toTweetTextEntityAnnotation() } ?: emptyList(),
                    mediaKeys = media[tweetId]?.map { MediaKeys(tweetId, it.mediaKey) } ?: emptyList()
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching tweet entities by IDs")
            emptyList()
        }
    }

    /**
     * Upload a single tweet and all its related data to Firestore
     */
    suspend fun uploadTweet(tweetEntities: TweetEntities) = withContext(Dispatchers.IO) {
        try {
            val tweetId = tweetEntities.tweetEntity.id

            // Idempotent upload: parent doc uses the tweet id as its
            // deterministic document key, with merge semantics so repeated
            // calls (e.g. two concurrent syncs) collapse into one doc instead
            // of racing through whereEqualTo()+set() and double-writing.
            val tweetRef = db.collection(TWEETS_COLLECTION).document(tweetId)
            val existingSnapshot = tweetRef.get().await()
            val isFirstWrite = !existingSnapshot.exists()
            if (!isFirstWrite) {
                Timber.d("Tweet already in Firestore — merging only")
            }

            // Use batch write for atomicity
            val batch = db.batch()

            // Add tweet (deterministic doc, merge-safe for repeat uploads)
            batch.set(tweetRef, FirestoreTweet.fromTweetEntity(tweetEntities.tweetEntity), SetOptions.merge())

            // Sub-collections only fan out on the first write — repeating them
            // on every call would multiply child docs and inflate read costs.
            if (!isFirstWrite) {
                batch.commit().await()
                return@withContext
            }

            // Add users
            tweetEntities.twitterUserEntity.forEach { user ->
                val userRef = db.collection(USERS_COLLECTION).document()
                batch.set(userRef, FirestoreUser.fromTwitterUserEntity(user))
            }

            // Add metrics
            val metricsRef = db.collection(METRICS_COLLECTION).document()
            batch.set(metricsRef, FirestoreMetrics.fromTweetPublicMetrics(tweetEntities.tweetPublicMetrics))

            // Add media
            tweetEntities.tweetMediaEntity.forEach { media ->
                val mediaRef = db.collection(MEDIA_COLLECTION).document()
                batch.set(mediaRef, FirestoreMedia.fromTweetMediaEntity(media))
            }

            // Add includes
            tweetEntities.tweetIncludesEntity.forEach { include ->
                val includeRef = db.collection(INCLUDES_COLLECTION).document()
                batch.set(includeRef, FirestoreIncludes.fromTweetIncludesEntity(include))
            }

            // Add text annotations
            tweetEntities.tweetTextEntity.forEach { annotation ->
                val annotationRef = db.collection(TEXT_ANNOTATIONS_COLLECTION).document()
                batch.set(annotationRef, FirestoreTextAnnotation.fromTweetTextEntityAnnotation(annotation))
            }

            batch.commit().await()
            Timber.d("Successfully uploaded tweet to Firestore")
        } catch (e: Exception) {
            Timber.e(e, "Error uploading tweet to Firestore")
        }
    }

    /**
     * Upload multiple tweets to Firestore in batches
     */
    suspend fun uploadTweets(tweets: List<TweetEntities>) = withContext(Dispatchers.IO) {
        tweets.chunked(BATCH_SIZE).forEach { batch ->
            batch.forEach { tweetEntities ->
                uploadTweet(tweetEntities)
            }
        }
    }

    /**
     * Sync local tweets to Firestore that don't exist there yet
     * @param localTweets List of all local TweetEntity objects
     */
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
