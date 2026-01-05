package com.github.jayteealao.reddit.di

import com.github.jayteealao.reddit.services.RedditApiService
import com.github.jayteealao.reddit.services.RedditAuthClient
import com.github.jayteealao.reddit.services.RedditAuthClientImpl
import com.github.jayteealao.reddit.services.RedditOAuthService
import com.skydoves.sandwich.adapters.ApiResponseCallAdapterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Hilt module for Reddit feature
 */
@Module
@InstallIn(SingletonComponent::class)
object RedditNetworkModule {

    /**
     * Provide Retrofit for Reddit API
     */
    @Provides
    @Singleton
    @RedditRetrofit
    fun provideRedditRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(RedditApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
            .build()
    }

    /**
     * Provide Retrofit for Reddit OAuth (different base URL)
     */
    @Provides
    @Singleton
    @RedditOAuthRetrofit
    fun provideRedditOAuthRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(RedditApiService.AUTH_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
            .build()
    }

    /**
     * Provide Reddit API Service
     */
    @Provides
    @Singleton
    fun provideRedditApiService(
        @RedditRetrofit retrofit: Retrofit
    ): RedditApiService {
        return retrofit.create(RedditApiService::class.java)
    }

    /**
     * Provide Reddit OAuth Service
     */
    @Provides
    @Singleton
    fun provideRedditOAuthService(
        @RedditOAuthRetrofit retrofit: Retrofit
    ): RedditOAuthService {
        return retrofit.create(RedditOAuthService::class.java)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RedditBindsModule {

    /**
     * Bind RedditAuthClient implementation
     */
    @Binds
    @Singleton
    abstract fun bindRedditAuthClient(
        impl: RedditAuthClientImpl
    ): RedditAuthClient
}

/**
 * Qualifiers to distinguish Reddit Retrofit instances
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RedditRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RedditOAuthRetrofit
