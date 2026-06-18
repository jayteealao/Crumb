package com.github.jayteealao.crumbs.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.jayteealao.crumbs.data.DeletedBookmarkDao
import com.github.jayteealao.crumbs.data.SyncProgressDao
import com.github.jayteealao.crumbs.db.ALL_MIGRATIONS
import com.github.jayteealao.crumbs.db.AppDatabase
import com.github.jayteealao.crumbs.db.RedditFtsDao
import com.github.jayteealao.crumbs.db.TweetFtsDao
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

    @Singleton
    @Provides
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "AppDatabase"
    )
        .addMigrations(*ALL_MIGRATIONS)
        .fallbackToDestructiveMigration(false)
        .addCallback(ftsBuildCallback)
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

    @Singleton
    @Provides
    fun providesTweetFtsDao(appDatabase: AppDatabase): TweetFtsDao = appDatabase.tweetFtsDao()

    @Singleton
    @Provides
    fun providesRedditFtsDao(appDatabase: AppDatabase): RedditFtsDao = appDatabase.redditFtsDao()

    @Singleton
    @Provides
    fun providesSyncProgressDao(appDatabase: AppDatabase): SyncProgressDao = appDatabase.syncProgressDao()
}

/**
 * Post-open callback that rebuilds the FTS4 shadow tables once, after the database is first
 * opened on a device that has just been migrated through v10→v11.
 *
 * Why here and not inside MIGRATION_10_11:
 *   The FTS 'rebuild' command re-tokenizes every row in the parent table synchronously inside
 *   the migration transaction, holding the SQLite write lock for O(N) time. On large corpora
 *   (10 000+ bookmarks) this can block for seconds and risks a busy-timeout. Moving it to
 *   onOpen lets it run outside any migration transaction and on a background thread that is
 *   not time-bounded by SQLite.
 *
 * Guard logic:
 *   The callback checks whether tweet_fts is empty while tweetEntity is non-empty (and
 *   likewise for reddit_fts / reddit_posts). This is the exact state the DB is in immediately
 *   after the v11 migration creates the empty virtual tables — the triggers maintain
 *   consistency thereafter, so the condition is only true once per install/upgrade.
 *   On subsequent opens both FTS tables are non-empty (or the parent is empty too), so the
 *   execSQL calls are skipped entirely.
 */
private val ftsBuildCallback = object : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        // Rebuild tweet_fts if the parent table has rows but FTS is empty.
        val tweetParentCount = db.query("SELECT COUNT(*) FROM `tweetEntity`").use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0L
        }
        val tweetFtsCount = db.query("SELECT COUNT(*) FROM `tweet_fts`").use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0L
        }
        if (tweetParentCount > 0 && tweetFtsCount == 0L) {
            db.execSQL("INSERT INTO `tweet_fts`(`tweet_fts`) VALUES('rebuild')")
        }

        // Rebuild reddit_fts if the parent table has rows but FTS is empty.
        val redditParentCount = db.query("SELECT COUNT(*) FROM `reddit_posts`").use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0L
        }
        val redditFtsCount = db.query("SELECT COUNT(*) FROM `reddit_fts`").use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0L
        }
        if (redditParentCount > 0 && redditFtsCount == 0L) {
            db.execSQL("INSERT INTO `reddit_fts`(`reddit_fts`) VALUES('rebuild')")
        }
    }
}
