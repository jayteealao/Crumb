package com.github.jayteealao.crumbs.di

import android.content.Context
import androidx.room.Room
import com.github.jayteealao.crumbs.data.AppDatabase
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
    ).build()

    @Singleton
    @Provides
    fun providesTweetDao(appDatabase: AppDatabase) = appDatabase.tweetDao()
}
