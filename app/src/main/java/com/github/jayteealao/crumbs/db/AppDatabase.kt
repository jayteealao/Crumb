package com.github.jayteealao.crumbs.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.github.jayteealao.twitter.data.TweetDao
import com.github.jayteealao.twitter.models.MediaKeys
import com.github.jayteealao.twitter.models.PollIds
import com.github.jayteealao.twitter.models.TweetContextAnnotationEntity
import com.github.jayteealao.twitter.models.TweetEntity
import com.github.jayteealao.twitter.models.TweetIncludesEntity
import com.github.jayteealao.twitter.models.TweetMediaEntity
import com.github.jayteealao.twitter.models.TweetPublicMetrics
import com.github.jayteealao.twitter.models.TweetReferencedTweets
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
        MediaKeys::class
    ],
    version = 2,
    exportSchema = true,
//    autoMigrations = [
//        AutoMigration(from = 1, to = 2)
//    ]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tweetDao(): TweetDao
}
