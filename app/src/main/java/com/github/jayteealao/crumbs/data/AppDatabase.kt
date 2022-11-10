package com.github.jayteealao.crumbs.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.github.jayteealao.crumbs.models.MediaKeys
import com.github.jayteealao.crumbs.models.PollIds
import com.github.jayteealao.crumbs.models.TweetContextAnnotationEntity
import com.github.jayteealao.crumbs.models.TweetEntity
import com.github.jayteealao.crumbs.models.TweetIncludesEntity
import com.github.jayteealao.crumbs.models.TweetMediaEntity
import com.github.jayteealao.crumbs.models.TweetPublicMetrics
import com.github.jayteealao.crumbs.models.TweetReferencedTweets
import com.github.jayteealao.crumbs.models.TweetTextEntityAnnotation
import com.github.jayteealao.crumbs.models.TwitterUserEntity

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
