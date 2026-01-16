package com.github.jayteealao.twitter.data

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
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
        mediaKeys: List<MediaKeys>,
        pollIds: PollIds?
    )

    @Transaction
    @Query("SELECT * FROM tweetEntity WHERE referenced = false ORDER BY `order` DESC")
    fun getTweets(): PagingSource<Int, TweetData>

    @Query("SELECT * FROM tweetEntity WHERE referenced = false ORDER BY `order` DESC")
    fun getLatestBookmark(): TweetEntity

    @Query("SELECT author_id, conversation_id FROM tweetEntity WHERE referenced = false ORDER BY `order` DESC")
    fun getLatestThreadId(): List<IdForThread>

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

data class IdForThread(
    @ColumnInfo(name = "author_id") val authorId: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String
)