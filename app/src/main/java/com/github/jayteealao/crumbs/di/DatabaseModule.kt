package com.github.jayteealao.crumbs.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.jayteealao.crumbs.data.DeletedBookmarkDao
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
        .addMigrations(
            MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
            MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
        )
        .build()

    @Singleton
    @Provides
    fun providesTweetDao(appDatabase: AppDatabase): TweetDao = appDatabase.tweetDao()

    @Singleton
    @Provides
    fun providesRedditDao(appDatabase: AppDatabase): RedditDao = appDatabase.redditDao()

    @Singleton
    @Provides
    fun providesDeletedBookmarkDao(appDatabase: AppDatabase): DeletedBookmarkDao = appDatabase.deletedBookmarkDao()
}

val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `deleted_bookmarks` (
                `bookmarkId` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `deletedAt` INTEGER NOT NULL,
                PRIMARY KEY(`bookmarkId`)
            )
        """.trimIndent())
    }
}

/**
 * v8 → v9: add Reddit-side tag cross-reference table.
 *
 * The Twitter cross-ref (`tweet_tags`) carries an FK to `tweetEntity.id`,
 * which made Reddit tag saves throw `SQLITE_CONSTRAINT_FOREIGNKEY` when
 * tagRepository was bound through the Twitter Repository. The new
 * `reddit_tag_crossref` table is source-scoped and has no parent FK so a
 * tag survives a post being purged from the local cache (and so multi-
 * source IDs cannot collide on insert).
 */
val MIGRATION_8_9: Migration = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `reddit_tag_crossref` (" +
                "`postId` TEXT NOT NULL, " +
                "`tagName` TEXT NOT NULL, " +
                "PRIMARY KEY(`postId`, `tagName`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reddit_tag_crossref_postId` ON `reddit_tag_crossref` (`postId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reddit_tag_crossref_tagName` ON `reddit_tag_crossref` (`tagName`)")
    }
}

/**
 * v7 → v8: index FK columns on pollIds and mediaKeys.
 *
 * Without these indexes Room must scan the entire child table whenever a
 * parent tweetEntity is deleted or its `id` is updated. KSP flagged this
 * as a high-priority warning; adding ordinary BTREE indexes keeps the
 * cascade behavior cheap regardless of how many polls/media rows exist.
 */
val MIGRATION_7_8: Migration = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pollIds_tweetId` ON `pollIds` (`tweetId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_mediaKeys_tweet_id` ON `mediaKeys` (`tweet_id`)")
    }
}

/**
 * v6 → v7: index `order` on tweetEntity and reddit_posts.
 *
 * The feed paging queries ORDER BY `order` DESC, but neither table had an
 * index on that column — Room degraded to a full-table scan on every page
 * boundary as the bookmark count grew. Adding plain BTREE indexes makes the
 * page query O(log n) seek + O(pageSize) read regardless of total size.
 */
val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tweetEntity_order` ON `tweetEntity` (`order`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reddit_posts_order` ON `reddit_posts` (`order`)")
    }
}

val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // SQLite cannot ALTER PRIMARY KEY — recreate the table with composite PK.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `deleted_bookmarks_new` (" +
                "`bookmarkId` TEXT NOT NULL, " +
                "`source` TEXT NOT NULL, " +
                "`deletedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`bookmarkId`, `source`))"
        )
        // Copy existing rows. If any (bookmarkId, source) duplicates exist they
        // collapse via INSERT OR IGNORE — the first row wins.
        db.execSQL(
            "INSERT OR IGNORE INTO `deleted_bookmarks_new` (`bookmarkId`, `source`, `deletedAt`) " +
                "SELECT `bookmarkId`, `source`, `deletedAt` FROM `deleted_bookmarks`"
        )
        db.execSQL("DROP TABLE `deleted_bookmarks`")
        db.execSQL("ALTER TABLE `deleted_bookmarks_new` RENAME TO `deleted_bookmarks`")
    }
}
