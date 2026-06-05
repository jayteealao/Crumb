package com.github.jayteealao.twitter.di

import com.github.jayteealao.twitter.data.TwitterSyncFacade
import com.github.jayteealao.twitter.data.TwitterSyncFacadeImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TwitterSyncModule {

    @Binds
    @Singleton
    abstract fun bindTwitterSyncFacade(impl: TwitterSyncFacadeImpl): TwitterSyncFacade
}
