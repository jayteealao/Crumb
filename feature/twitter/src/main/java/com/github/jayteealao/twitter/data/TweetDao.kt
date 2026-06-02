package com.github.jayteealao.twitter.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.github.jayteealao.twitter.models.MediaKeys
import com.github.jayteealao.twitter.models.PollIds
import com.github.jayteealao.twitter.models.TagEntity
import com.github.jayteealao.twitter.models.TweetContextAnnotationEntity
import com.github.jayteealao.twitter.models.TweetData
import com.github.jayteealao.twitter.models.TweetEntities
import com.github.jayteealao.twitter.models.TweetEntity
import com.github.jayteealao.twitter.models.TweetIncludesEntity
import com.github.jayteealao.twitter.models.TweetMediaEntity
import com.github.jayteealao.twitter.models.TweetPublicMetrics
import com.github.jayteealao.twitter.models.TweetReferencedTweets
import com.github.jayteealao.twitter.models.TweetTagCrossRef
import com.github.jayteealao.twitter.models.TweetTextEntityAnnotation
import com.github.jayteealao.twitter.models.TwitterUserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TweetDao {
    @Insert
    fun insertTweet(tweet: TweetEntity)

    @Insert
    fun insertTwitterUser(user: TwitterUserEntity)

    @Insert
    fun insertTweetMedia(tweetMedia: TweetMediaEntity)

    @Insert
    fun insertTweetIncludes(tweetIncludes: TweetIncludesEntity)

    @Insert
    fun insertTweetReferencedTweets(TweetReferencedTweets: TweetReferencedTweets)

    @Insert
    fun insertTweetContextAnnotation(tweetContextAnnotation: TweetContextAnnotationEntity)

    @Insert
    fun insertTweetPublicMetrics(tweetPublicMetrics: TweetPublicMetrics)

    @Insert
    fun insertTweetTextEntityAnnotation(tweetTextEntityAnnotation: TweetTextEntityAnnotation)

    @Insert
    fun insertPollId(pollIds: PollIds)

    @Insert
    fun insertMediaKeys(mediaKeys: MediaKeys)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertTweetEntities(
        tweet: TweetEntity,
        tweetsReferenced: List<TweetEntity>,
        twitterUserEntity: List<TwitterUserEntity>,
        tweetPublicMetrics: TweetPublicMetrics,
        tweetMediaEntity: List<TweetMediaEntity>,
        tweetIncludesEntity: List<TweetIncludesEntity>,
        tweetReferencedTweets: List<TweetReferencedTweets>,
        tweetContextAnnotationEntity: List<TweetContextAnnotationEntity>,
        tweetTextEntity: List<TweetTextEntityAnnotation>,
        mediaKeys: List<MediaKeys>
    )

    /**
     * Atomic write of the full tweet aggregate. Wraps the multi-entity insert and
     * the optional pollIds insert in one SQLite transaction so Paging3's
     * InvalidationTracker emits exactly one invalidation and never observes a
     * partially-hydrated row (orphaned pollIds, missing public-metrics, etc.).
     */
    @Transaction
    fun insertTweetEntitiesAtomic(
        tweet: TweetEntity,
        tweetsReferenced: List<TweetEntity>,
        twitterUserEntity: List<TwitterUserEntity>,
        tweetPublicMetrics: TweetPublicMetrics,
        tweetMediaEntity: List<TweetMediaEntity>,
        tweetIncludesEntity: List<TweetIncludesEntity>,
        tweetReferencedTweets: List<TweetReferencedTweets>,
        tweetContextAnnotationEntity: List<TweetContextAnnotationEntity>,
        tweetTextEntity: List<TweetTextEntityAnnotation>,
        mediaKeys: List<MediaKeys>,
        pollIds: PollIds?,
    ) {
        insertTweetEntities(
            tweet,
            tweetsReferenced,
            twitterUserEntity,
            tweetPublicMetrics,
            tweetMediaEntity,
            tweetIncludesEntity,
            tweetReferencedTweets,
            tweetContextAnnotationEntity,
            tweetTextEntity,
            mediaKeys,
        )
        pollIds?.let { insertPollId(it) }
    }

    /**
     * Insert a whole batch of tweet aggregates in one Room transaction so the
     * Paging source's `InvalidationTracker` fires exactly once per batch (not
     * once per row). Used by the streaming sync worker; the orchestrator
     * wraps this call + `SyncProgressDao.upsert(...)` in `db.withTransaction`
     * so cursor advancement and the batch insert commit atomically.
     */
    @Transaction
    fun insertTweetEntitiesBatch(batch: List<TweetEntities>) {
        batch.forEach { entities ->
            insertTweetEntitiesAtomic(
                entities.tweetEntity,
                entities.tweetReferencedTweets.mapNotNull { it.tweet },
                entities.twitterUserEntity,
                entities.tweetPublicMetrics,
                entities.tweetMediaEntity,
                entities.tweetIncludesEntity,
                entities.tweetReferencedTweets.map { it.referencedTweets },
                entities.tweetContextAnnotationEntity,
                entities.tweetTextEntity,
                entities.mediaKeys,
                entities.pollIds,
            )
        }
    }

    @Transaction
    @Query("SELECT rowid AS db_rowid, * FROM tweetEntity WHERE referenced = false ORDER BY retrieved_at DESC, created_at DESC")
    fun getTweets(): PagingSource<Int, TweetData>

    @Transaction
    @Query("""
        SELECT t.rowid AS db_rowid, t.* FROM tweetEntity t
        LEFT JOIN deleted_bookmarks d ON t.id = d.bookmarkId AND d.source = 'twitter'
        WHERE t.referenced = 0
          AND d.bookmarkId IS NULL
          AND (
            :type = 'ALL'
            OR (:type = 'IMAGE'   AND EXISTS (SELECT 1 FROM tweetMedia m WHERE m.tweet_id = t.id AND m.type = 'photo'))
            OR (:type = 'VIDEO'   AND EXISTS (SELECT 1 FROM tweetMedia m WHERE m.tweet_id = t.id AND m.type IN ('video','animated_gif')))
            OR (:type = 'ARTICLE' AND EXISTS (SELECT 1 FROM tweetTextEntityAnnotation a
                  WHERE a.tweet_id = t.id AND a.type = 'urls' AND a.expanded_url IS NOT NULL
                    AND a.expanded_url NOT LIKE '%twitter.com%' AND a.expanded_url NOT LIKE '%x.com%'))
            OR (:type = 'THREAD'  AND (t.conversation_id <> t.id
                  OR EXISTS (SELECT 1 FROM tweetEntity s WHERE s.conversation_id = t.conversation_id AND s.id <> t.id AND s.referenced = 0)))
            OR (:type = 'TEXT'    AND NOT EXISTS (SELECT 1 FROM tweetMedia m WHERE m.tweet_id = t.id)
                  AND NOT EXISTS (SELECT 1 FROM tweetTextEntityAnnotation a WHERE a.tweet_id = t.id AND a.type = 'urls'
                    AND a.expanded_url IS NOT NULL AND a.expanded_url NOT LIKE '%twitter.com%' AND a.expanded_url NOT LIKE '%x.com%'))
          )
        ORDER BY t.retrieved_at DESC, t.created_at DESC
    """)
    fun getTweetsTombstoneAware(type: String): PagingSource<Int, TweetData>

    @Transaction
    @Query("""
        SELECT t.rowid AS db_rowid, t.* FROM tweetEntity t
        LEFT JOIN deleted_bookmarks d ON t.id = d.bookmarkId AND d.source = 'twitter'
        INNER JOIN tweet_tags tt ON tt.tweetId = t.id
        WHERE t.referenced = 0
          AND d.bookmarkId IS NULL
          AND tt.tagName IN (:tagNames)
          AND (
            :type = 'ALL'
            OR (:type = 'IMAGE'   AND EXISTS (SELECT 1 FROM tweetMedia m WHERE m.tweet_id = t.id AND m.type = 'photo'))
            OR (:type = 'VIDEO'   AND EXISTS (SELECT 1 FROM tweetMedia m WHERE m.tweet_id = t.id AND m.type IN ('video','animated_gif')))
            OR (:type = 'ARTICLE' AND EXISTS (SELECT 1 FROM tweetTextEntityAnnotation a
                  WHERE a.tweet_id = t.id AND a.type = 'urls' AND a.expanded_url IS NOT NULL
                    AND a.expanded_url NOT LIKE '%twitter.com%' AND a.expanded_url NOT LIKE '%x.com%'))
            OR (:type = 'THREAD'  AND (t.conversation_id <> t.id
                  OR EXISTS (SELECT 1 FROM tweetEntity s WHERE s.conversation_id = t.conversation_id AND s.id <> t.id AND s.referenced = 0)))
            OR (:type = 'TEXT'    AND NOT EXISTS (SELECT 1 FROM tweetMedia m WHERE m.tweet_id = t.id)
                  AND NOT EXISTS (SELECT 1 FROM tweetTextEntityAnnotation a WHERE a.tweet_id = t.id AND a.type = 'urls'
                    AND a.expanded_url IS NOT NULL AND a.expanded_url NOT LIKE '%twitter.com%' AND a.expanded_url NOT LIKE '%x.com%'))
          )
        GROUP BY t.id
        ORDER BY t.retrieved_at DESC, t.created_at DESC
    """)
    fun getTweetsByTagsTombstoneAware(tagNames: List<String>, type: String): PagingSource<Int, TweetData>

    /**
     * Reactive count of the visible (no-tag) feed for the SAVED header. The WHERE
     * is kept byte-for-byte identical to [getTweetsTombstoneAware] — same tombstone
     * filter, same `:type` predicate block — so the header can never disagree with
     * the rendered list. Emits via Room's InvalidationTracker on any matching write.
     */
    @Query("""
        SELECT COUNT(*) FROM tweetEntity t
        LEFT JOIN deleted_bookmarks d ON t.id = d.bookmarkId AND d.source = 'twitter'
        WHERE t.referenced = 0
          AND d.bookmarkId IS NULL
          AND (
            :type = 'ALL'
            OR (:type = 'IMAGE'   AND EXISTS (SELECT 1 FROM tweetMedia m WHERE m.tweet_id = t.id AND m.type = 'photo'))
            OR (:type = 'VIDEO'   AND EXISTS (SELECT 1 FROM tweetMedia m WHERE m.tweet_id = t.id AND m.type IN ('video','animated_gif')))
            OR (:type = 'ARTICLE' AND EXISTS (SELECT 1 FROM tweetTextEntityAnnotation a
                  WHERE a.tweet_id = t.id AND a.type = 'urls' AND a.expanded_url IS NOT NULL
                    AND a.expanded_url NOT LIKE '%twitter.com%' AND a.expanded_url NOT LIKE '%x.com%'))
            OR (:type = 'THREAD'  AND (t.conversation_id <> t.id
                  OR EXISTS (SELECT 1 FROM tweetEntity s WHERE s.conversation_id = t.conversation_id AND s.id <> t.id AND s.referenced = 0)))
            OR (:type = 'TEXT'    AND NOT EXISTS (SELECT 1 FROM tweetMedia m WHERE m.tweet_id = t.id)
                  AND NOT EXISTS (SELECT 1 FROM tweetTextEntityAnnotation a WHERE a.tweet_id = t.id AND a.type = 'urls'
                    AND a.expanded_url IS NOT NULL AND a.expanded_url NOT LIKE '%twitter.com%' AND a.expanded_url NOT LIKE '%x.com%'))
          )
    """)
    fun countTombstoneAware(type: String): Flow<Int>

    /**
     * Reactive count for the tag-filtered feed. Mirrors [getTweetsByTagsTombstoneAware]'s
     * WHERE exactly; uses `COUNT(DISTINCT t.id)` because the `tweet_tags` INNER JOIN can
     * fan a single tweet across multiple matching tags (the list query collapses those
     * with `GROUP BY t.id`).
     */
    @Query("""
        SELECT COUNT(DISTINCT t.id) FROM tweetEntity t
        LEFT JOIN deleted_bookmarks d ON t.id = d.bookmarkId AND d.source = 'twitter'
        INNER JOIN tweet_tags tt ON tt.tweetId = t.id
        WHERE t.referenced = 0
          AND d.bookmarkId IS NULL
          AND tt.tagName IN (:tagNames)
          AND (
            :type = 'ALL'
            OR (:type = 'IMAGE'   AND EXISTS (SELECT 1 FROM tweetMedia m WHERE m.tweet_id = t.id AND m.type = 'photo'))
            OR (:type = 'VIDEO'   AND EXISTS (SELECT 1 FROM tweetMedia m WHERE m.tweet_id = t.id AND m.type IN ('video','animated_gif')))
            OR (:type = 'ARTICLE' AND EXISTS (SELECT 1 FROM tweetTextEntityAnnotation a
                  WHERE a.tweet_id = t.id AND a.type = 'urls' AND a.expanded_url IS NOT NULL
                    AND a.expanded_url NOT LIKE '%twitter.com%' AND a.expanded_url NOT LIKE '%x.com%'))
            OR (:type = 'THREAD'  AND (t.conversation_id <> t.id
                  OR EXISTS (SELECT 1 FROM tweetEntity s WHERE s.conversation_id = t.conversation_id AND s.id <> t.id AND s.referenced = 0)))
            OR (:type = 'TEXT'    AND NOT EXISTS (SELECT 1 FROM tweetMedia m WHERE m.tweet_id = t.id)
                  AND NOT EXISTS (SELECT 1 FROM tweetTextEntityAnnotation a WHERE a.tweet_id = t.id AND a.type = 'urls'
                    AND a.expanded_url IS NOT NULL AND a.expanded_url NOT LIKE '%twitter.com%' AND a.expanded_url NOT LIKE '%x.com%'))
          )
    """)
    fun countByTagsTombstoneAware(tagNames: List<String>, type: String): Flow<Int>

    @Query("SELECT * FROM tweetEntity WHERE referenced = false ORDER BY `order` DESC LIMIT 1")
    fun getLatestBookmark(): TweetEntity?

    /**
     * Single-bookmark fetch used by ThreadDetail navigation. Returns the full
     * [TweetData] aggregate so the caller can render the root card without a
     * second author/media round trip. Tombstoned and referenced rows are
     * excluded — opening a thread for a deleted bookmark falls through to a
     * caller-side empty state.
     */
    @Transaction
    @Query(
        """
        SELECT t.rowid AS db_rowid, t.* FROM tweetEntity t
        LEFT JOIN deleted_bookmarks d ON t.id = d.bookmarkId AND d.source = 'twitter'
        WHERE t.id = :id
          AND t.referenced = 0
          AND d.bookmarkId IS NULL
        LIMIT 1
        """
    )
    suspend fun getTweetById(id: String): TweetData?

    /**
     * All bookmarked tweets sharing the given Twitter conversation id, ordered
     * chronologically. Backs the ThreadDetail reply rendering — the root is
     * filtered out at the VM layer (the conversation includes the root tweet
     * by definition). Tombstone-aware so deleted replies do not surface.
     */
    @Transaction
    @Query(
        """
        SELECT t.rowid AS db_rowid, t.* FROM tweetEntity t
        LEFT JOIN deleted_bookmarks d ON t.id = d.bookmarkId AND d.source = 'twitter'
        WHERE t.conversation_id = :conversationId
          AND d.bookmarkId IS NULL
        ORDER BY t.created_at ASC
        """
    )
    fun tweetsByConversationId(conversationId: String): Flow<List<TweetData>>

    @Query("SELECT id FROM tweetEntity WHERE referenced = false")
    suspend fun getAllTweetIds(): List<String>

    /**
     * Tweet ids for the one-time media backfill: non-referenced, non-tombstoned
     * tweets that currently have NO `tweetMedia` rows. Keyset-paginated by id
     * (`id > :afterId`, ascending) so the backfill worker sweeps each media-less
     * tweet exactly once and terminates when a page returns empty — even though
     * genuinely media-less (text) tweets never leave the result set, the cursor
     * still advances past them. Used only by `MediaBackfillWorker`.
     */
    @Query(
        """
        SELECT t.id FROM tweetEntity t
        LEFT JOIN deleted_bookmarks d ON t.id = d.bookmarkId AND d.source = 'twitter'
        WHERE t.referenced = 0
          AND d.bookmarkId IS NULL
          AND t.id > :afterId
          AND NOT EXISTS (SELECT 1 FROM tweetMedia m WHERE m.tweet_id = t.id)
        ORDER BY t.id ASC
        LIMIT :limit
        """
    )
    suspend fun getTweetsWithoutMedia(afterId: String, limit: Int): List<String>

    @Query("SELECT MAX(`order`) FROM tweetEntity")
    suspend fun getMaxOrder(): Int?

    /**
     * Flip the per-tweet `pending_delete` flag. Driven by the cancel-swipe path
     * (sets value=false); the confirm-swipe path writes a tombstone via
     * DeletedBookmarkDao instead and does not call this.
     */
    @Query("UPDATE tweetEntity SET pending_delete = :value WHERE id = :id")
    suspend fun updatePendingDelete(id: String, value: Boolean)

    // Tag operations
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTweetTag(tweetTag: TweetTagCrossRef)

    @Query("SELECT tags.name FROM tags INNER JOIN tweet_tags ON tags.name = tweet_tags.tagName WHERE tweet_tags.tweetId = :tweetId")
    suspend fun getTagsForTweet(tweetId: String): List<String>

    /**
     * Batch variant — fetches tag names for multiple tweet ids in a single query.
     * Returns all matching rows as (tweetId, tagName) pairs; callers group by tweetId.
     */
    @Query("SELECT tweetId, tagName FROM tweet_tags WHERE tweetId IN (:tweetIds)")
    suspend fun getTagsForTweets(tweetIds: List<String>): List<TweetTagCrossRef>

    @Query("SELECT * FROM tags ORDER BY name ASC")
    suspend fun getAllTags(): List<TagEntity>

    @Query("DELETE FROM tweet_tags WHERE tweetId = :tweetId AND tagName = :tagName")
    suspend fun deleteTweetTag(tweetId: String, tagName: String)

    @Query("DELETE FROM tags WHERE name = :tagName")
    suspend fun deleteTag(tagName: String)
}