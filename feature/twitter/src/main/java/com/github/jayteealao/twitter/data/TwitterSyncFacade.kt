package com.github.jayteealao.twitter.data

import com.github.jayteealao.twitter.models.TweetEntities
import kotlinx.coroutines.flow.Flow

/**
 * Thin port exposing only what the app-layer sync workers need from the Twitter
 * data layer. Keeps [app/sync] free of direct [TweetDao] and
 * [com.github.jayteealao.twitter.data.firestore.FirestoreRepository] imports.
 *
 * All methods delegate to the existing [TweetDao] / FirestoreRepository public
 * APIs without modifying those classes.
 */
interface TwitterSyncFacade {

    /**
     * Returns all non-referenced (i.e. top-level bookmark) tweet IDs stored locally.
     * May return up to ~25 K IDs for a large corpus.
     */
    suspend fun getAllTweetIds(): List<String>

    /**
     * Returns the highest `order` value currently stored in the tweet table, or
     * null if the table is empty.
     */
    suspend fun getMaxOrder(): Int?

    /**
     * Inserts a batch of tweet aggregates atomically inside a single Room
     * transaction. IGNORE-on-conflict — already-present rows are skipped.
     */
    fun insertTweetEntitiesBatch(batch: List<TweetEntities>)

    /**
     * Returns a cold [Flow] of tweet-entity batches that are present in
     * Firestore for the signed-in user but absent from the local [localIds] set
     * (and not in [deletedIds]). Each emission is a page of ~30 aggregates ready
     * for atomic Room insertion.
     */
    fun fetchMissingTweetsStream(
        localIds: Set<String>,
        deletedIds: Set<String> = emptySet(),
    ): Flow<List<TweetEntities>>

    // --- Backfill sweep queries (used by MediaBackfillWorker) ---

    /** Keyset-paginated IDs of non-referenced, non-tombstoned tweets with no media rows. */
    suspend fun getTweetsWithoutMedia(afterId: String, limit: Int): List<String>

    /** Keyset-paginated IDs of video/gif tweets whose `video_variants` column is still NULL. */
    suspend fun getVideoTweetsWithoutVariants(afterId: String, limit: Int): List<String>

    /** Keyset-paginated IDs of tweets with no external URL-entity annotation yet. */
    suspend fun getExternalLinkTweetsWithoutPreview(afterId: String, limit: Int): List<String>

    /** Keyset-paginated IDs of tweets whose quoted body has not yet been stored locally. */
    suspend fun getQuoteTweetsWithoutBody(afterId: String, limit: Int): List<String>
}
