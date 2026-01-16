package com.github.jayteealao.crumbs.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.jayteealao.crumbs.db.AppDatabase
import com.github.jayteealao.reddit.data.RedditDao
import com.github.jayteealao.twitter.data.TweetDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class DatabaseModule {

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Create tags table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `tags` (
                    `name` TEXT NOT NULL,
                    PRIMARY KEY(`name`)
                )
            """.trimIndent())

            // Create tweet_tags cross-reference table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `tweet_tags` (
                    `tweetId` TEXT NOT NULL,
                    `tagName` TEXT NOT NULL,
                    PRIMARY KEY(`tweetId`, `tagName`),
                    FOREIGN KEY(`tweetId`) REFERENCES `tweetentity`(`id`) ON DELETE CASCADE,
                    FOREIGN KEY(`tagName`) REFERENCES `tags`(`name`) ON DELETE CASCADE
                )
            """.trimIndent())

            // Create indices for better query performance
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tweet_tags_tweetId` ON `tweet_tags` (`tweetId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tweet_tags_tagName` ON `tweet_tags` (`tagName`)")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Fix tweet_tags table foreign key reference case issue
            // Save existing data
            db.execSQL("""
                CREATE TEMPORARY TABLE `tweet_tags_backup` (
                    `tweetId` TEXT NOT NULL,
                    `tagName` TEXT NOT NULL
                )
            """.trimIndent())

            db.execSQL("INSERT INTO `tweet_tags_backup` SELECT `tweetId`, `tagName` FROM `tweet_tags`")

            // Drop the old table
            db.execSQL("DROP TABLE `tweet_tags`")

            // Recreate with correct foreign key reference
            db.execSQL("""
                CREATE TABLE `tweet_tags` (
                    `tweetId` TEXT NOT NULL,
                    `tagName` TEXT NOT NULL,
                    PRIMARY KEY(`tweetId`, `tagName`),
                    FOREIGN KEY(`tweetId`) REFERENCES `tweetEntity`(`id`) ON DELETE CASCADE,
                    FOREIGN KEY(`tagName`) REFERENCES `tags`(`name`) ON DELETE CASCADE
                )
            """.trimIndent())

            // Restore data
            db.execSQL("INSERT INTO `tweet_tags` SELECT `tweetId`, `tagName` FROM `tweet_tags_backup`")
            db.execSQL("DROP TABLE `tweet_tags_backup`")

            // Recreate indices
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tweet_tags_tweetId` ON `tweet_tags` (`tweetId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tweet_tags_tagName` ON `tweet_tags` (`tagName`)")

            // Create reddit_posts table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `reddit_posts` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `selftext` TEXT NOT NULL,
                    `author` TEXT NOT NULL,
                    `subreddit` TEXT NOT NULL,
                    `subreddit_prefixed` TEXT NOT NULL,
                    `created_utc` INTEGER NOT NULL,
                    `url` TEXT NOT NULL,
                    `permalink` TEXT NOT NULL,
                    `thumbnail` TEXT,
                    `num_comments` INTEGER NOT NULL,
                    `score` INTEGER NOT NULL,
                    `is_self` INTEGER NOT NULL,
                    `is_video` INTEGER NOT NULL,
                    `domain` TEXT NOT NULL,
                    `link_flair_text` TEXT,
                    `gilded` INTEGER NOT NULL,
                    `over_18` INTEGER NOT NULL,
                    `order` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())

            // Create indices for Reddit posts
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reddit_posts_author` ON `reddit_posts` (`author`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reddit_posts_subreddit` ON `reddit_posts` (`subreddit`)")
        }
    }

    @Singleton
    @Provides
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "AppDatabase"
    )
        .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
        .build()

    @Singleton
    @Provides
    fun providesTweetDao(appDatabase: AppDatabase): TweetDao = appDatabase.tweetDao()

    @Singleton
    @Provides
    fun providesRedditDao(appDatabase: AppDatabase): RedditDao = appDatabase.redditDao()
}
