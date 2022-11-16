package com.github.jayteealao.crumbs.di

import com.github.jayteealao.crumbs.services.twitter.TwitterApiService
import com.github.jayteealao.crumbs.services.twitter.TwitterAuthService
import com.skydoves.sandwich.adapters.ApiResponseCallAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpRequestInterceptor())
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl("https://api.twitter.com")
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideTwitterApiService(retrofit: Retrofit): TwitterApiService {
        return retrofit.create(TwitterApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideTwitterAuthService(retrofit: Retrofit): TwitterAuthService {
        return retrofit.create(TwitterAuthService::class.java)
    }
}

internal class HttpRequestInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val request = originalRequest
            .newBuilder()
            .url(originalRequest.url)
            .headers(originalRequest.headers)
            .addHeader("User-Agent", "CrumbsTwitter")
            .addHeader("Accept", "application/json")
            .build()
        Timber.d(request.toString())
        Timber.d(request.headers.toString())
        return chain.proceed(request)
    }
}
