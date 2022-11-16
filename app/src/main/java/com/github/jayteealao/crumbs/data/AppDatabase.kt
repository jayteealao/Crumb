package com.github.jayteealao.crumbs.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.github.jayteealao.crumbs.models.twitter.MediaKeys
import com.github.jayteealao.crumbs.models.twitter.PollIds
import com.github.jayteealao.crumbs.models.twitter.TweetContextAnnotationEntity
import com.github.jayteealao.crumbs.models.twitter.TweetEntity
import com.github.jayteealao.crumbs.models.twitter.TweetIncludesEntity
import com.github.jayteealao.crumbs.models.twitter.TweetMediaEntity
import com.github.jayteealao.crumbs.models.twitter.TweetPublicMetrics
import com.github.jayteealao.crumbs.models.twitter.TweetReferencedTweets
import com.github.jayteealao.crumbs.models.twitter.TweetTextEntityAnnotation
import com.github.jayteealao.crumbs.models.twitter.TwitterUserEntity

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
    autoMigrations = [
        AutoMigration(from = 1, to = 2)
    ]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tweetDao(): TweetDao
}
