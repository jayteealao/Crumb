package com.github.jayteealao.twitter.data

import com.github.jayteealao.twitter.data.firestore.SyncCursor
import com.github.jayteealao.twitter.data.firestore.SyncEmission
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
     * Returns a cold [Flow] of cursor-narrowed sync batches for the signed-in user, resuming from
     * [resumeFrom] (the persisted cursor) rather than re-enumerating the whole Firestore corpus.
     * Each [SyncEmission] carries a page of ~30 aggregates absent from [localIds] (and not in
     * [deletedIds]) PLUS the cursor to persist atomically with that batch's insert; a final
     * empty-entity emission checkpoints the advanced cursor on a nothing-new run.
     */
    fun fetchMissingTweetsStream(
        localIds: Set<String>,
        deletedIds: Set<String> = emptySet(),
        resumeFrom: SyncCursor = SyncCursor(),
    ): Flow<SyncEmission>

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
