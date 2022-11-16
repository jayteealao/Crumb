package com.github.jayteealao.crumbs.di

import com.github.jayteealao.crumbs.services.twitter.TwitterApiClient
import com.github.jayteealao.crumbs.services.twitter.TwitterApiClientImpl
import com.github.jayteealao.crumbs.services.twitter.TwitterAuthClient
import com.github.jayteealao.crumbs.services.twitter.TwitterAuthClientImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {
    @Binds
    abstract fun provideTwitterAuthService(impl: TwitterAuthClientImpl): TwitterAuthClient

    @Binds
    abstract fun provideTwitterApiClient(impl: TwitterApiClientImpl): TwitterApiClient
}
