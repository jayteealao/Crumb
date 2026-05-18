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
import com.github.jayteealao.twitter.models.TweetEntity
import com.github.jayteealao.twitter.models.TweetIncludesEntity
import com.github.jayteealao.twitter.models.TweetMediaEntity
import com.github.jayteealao.twitter.models.TweetPublicMetrics
import com.github.jayteealao.twitter.models.TweetReferencedTweets
import com.github.jayteealao.twitter.models.TweetTagCrossRef
import com.github.jayteealao.twitter.models.TweetTextEntityAnnotation
import com.github.jayteealao.twitter.models.TwitterUserEntity

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

    @Transaction
    @Query("SELECT * FROM tweetEntity WHERE referenced = false ORDER BY `order` DESC")
    fun getTweets(): PagingSource<Int, TweetData>

    @Transaction
    @Query("""
        SELECT t.* FROM tweetEntity t
        LEFT JOIN deleted_bookmarks d ON t.id = d.bookmarkId
        WHERE t.referenced = 0
          AND d.bookmarkId IS NULL
        ORDER BY t.`order` DESC
    """)
    fun getTweetsTombstoneAware(): PagingSource<Int, TweetData>

    @Transaction
    @Query("""
        SELECT t.* FROM tweetEntity t
        LEFT JOIN deleted_bookmarks d ON t.id = d.bookmarkId
        INNER JOIN tweet_tags tt ON tt.tweetId = t.id
        WHERE t.referenced = 0
          AND d.bookmarkId IS NULL
          AND tt.tagName IN (:tagNames)
        GROUP BY t.id
        ORDER BY t.`order` DESC
    """)
    fun getTweetsByTagsTombstoneAware(tagNames: List<String>): PagingSource<Int, TweetData>

    @Query("SELECT * FROM tweetEntity WHERE referenced = false ORDER BY `order` DESC LIMIT 1")
    fun getLatestBookmark(): TweetEntity?

    @Query("SELECT id FROM tweetEntity WHERE referenced = false")
    suspend fun getAllTweetIds(): List<String>

    @Query("SELECT MAX(`order`) FROM tweetEntity")
    suspend fun getMaxOrder(): Int?

    // Tag operations
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTweetTag(tweetTag: TweetTagCrossRef)

    @Query("SELECT tags.name FROM tags INNER JOIN tweet_tags ON tags.name = tweet_tags.tagName WHERE tweet_tags.tweetId = :tweetId")
    suspend fun getTagsForTweet(tweetId: String): List<String>

    @Query("SELECT * FROM tags ORDER BY name ASC")
    suspend fun getAllTags(): List<TagEntity>

    @Query("DELETE FROM tweet_tags WHERE tweetId = :tweetId AND tagName = :tagName")
    suspend fun deleteTweetTag(tweetId: String, tagName: String)

    @Query("DELETE FROM tags WHERE name = :tagName")
    suspend fun deleteTag(tagName: String)
}