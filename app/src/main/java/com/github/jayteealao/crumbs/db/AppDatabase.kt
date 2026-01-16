package com.github.jayteealao.crumbs.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.github.jayteealao.reddit.data.RedditDao
import com.github.jayteealao.reddit.models.RedditPostEntity
import com.github.jayteealao.twitter.data.TweetDao
import com.github.jayteealao.twitter.models.MediaKeys
import com.github.jayteealao.twitter.models.PollIds
import com.github.jayteealao.twitter.models.TagEntity
import com.github.jayteealao.twitter.models.TweetContextAnnotationEntity
import com.github.jayteealao.twitter.models.TweetEntity
import com.github.jayteealao.twitter.models.TweetIncludesEntity
import com.github.jayteealao.twitter.models.TweetMediaEntity
import com.github.jayteealao.twitter.models.TweetPublicMetrics
import com.github.jayteealao.twitter.models.TweetReferencedTweets
import com.github.jayteealao.twitter.models.TweetTagCrossRef
import com.github.jayteealao.twitter.models.TweetTextEntityAnnotation
import com.github.jayteealao.twitter.models.TwitterUserEntity

@Database(
    entities = [
        TweetEntity::class,
        TwitterUserEntity::class,
        TweetMediaEntity::class,
        TweetIncludesEntity::class,
        TweetReferencedTweets::class,
        TweetContextAnnotationEntity::class,
        TweetPublicMetrics::class,
        TweetTextEntityAnnotation::class,
        PollIds::class,
        MediaKeys::class,
        TagEntity::class,
        TweetTagCrossRef::class,
        RedditPostEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tweetDao(): TweetDao
    abstract fun redditDao(): RedditDao
}
