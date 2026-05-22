package com.github.jayteealao.twitter.data.firestore

import com.github.jayteealao.twitter.models.MediaKeys
import com.github.jayteealao.twitter.models.TweetEntities
import com.github.jayteealao.twitter.models.TweetEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

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

    suspend fun fetchTweetsNotInLocal(localIds: Set<String>): List<TweetEntities> = withContext(Dispatchers.IO) {
        try {
            val firestoreIds = getAllTweetIds()
            val missingIds = firestoreIds - localIds

            if (missingIds.isEmpty()) {
                Timber.d("No missing tweets to fetch from Firestore")
                return@withContext emptyList()
            }

            Timber.d("Fetching ${missingIds.size} tweets from Firestore")

            // Firestore "in" query cap is 30.
            missingIds.chunked(30).flatMap { batch ->
                fetchTweetEntitiesByIds(batch)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching tweets from Firestore")
            emptyList()
        }
    }

    private suspend fun fetchTweetEntitiesByIds(tweetIds: List<String>): List<TweetEntities> = coroutineScope {
        if (tweetIds.isEmpty()) return@coroutineScope emptyList()
        val uid = requireUid()

        try {
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

            val mediaDeferred = async {
                mediaCol(uid)
                    .whereIn("tweetId", tweetIds)
                    .get()
                    .await()
                    .toObjects(FirestoreMedia::class.java)
                    .groupBy { it.tweetId ?: "" }
            }

            val includesDeferred = async {
                includesCol(uid)
                    .whereIn("tweetId", tweetIds)
                    .get()
                    .await()
                    .toObjects(FirestoreIncludes::class.java)
                    .groupBy { it.tweetId }
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
            val media = mediaDeferred.await()
            val includes = includesDeferred.await()
            val textAnnotations = textAnnotationsDeferred.await()

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
