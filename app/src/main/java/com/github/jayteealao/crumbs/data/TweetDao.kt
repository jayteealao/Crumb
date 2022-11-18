package com.github.jayteealao.crumbs.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.github.jayteealao.crumbs.models.twitter.MediaKeys
import com.github.jayteealao.crumbs.models.twitter.PollIds
import com.github.jayteealao.crumbs.models.twitter.TweetContextAnnotationEntity
import com.github.jayteealao.crumbs.models.twitter.TweetData
import com.github.jayteealao.crumbs.models.twitter.TweetEntity
import com.github.jayteealao.crumbs.models.twitter.TweetIncludesEntity
import com.github.jayteealao.crumbs.models.twitter.TweetMediaEntity
import com.github.jayteealao.crumbs.models.twitter.TweetPublicMetrics
import com.github.jayteealao.crumbs.models.twitter.TweetReferencedTweets
import com.github.jayteealao.crumbs.models.twitter.TweetTextEntityAnnotation
import com.github.jayteealao.crumbs.models.twitter.TwitterUserEntity

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
}
