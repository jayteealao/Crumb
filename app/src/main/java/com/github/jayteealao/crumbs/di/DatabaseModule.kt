package com.github.jayteealao.crumbs.di

import android.content.Context
import androidx.room.Room
import com.github.jayteealao.crumbs.data.DeletedBookmarkDao
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
}
