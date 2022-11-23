package com.github.jayteealao.twitter.di

import com.github.jayteealao.twitter.services.TwitterApiClient
import com.github.jayteealao.twitter.services.TwitterApiClientImpl
import com.github.jayteealao.twitter.services.TwitterAuthClient
import com.github.jayteealao.twitter.services.TwitterAuthClientImpl
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