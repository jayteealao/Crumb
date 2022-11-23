package com.github.jayteealao.crumbs.di

//
//@Module
//@InstallIn(SingletonComponent::class)
//object NetworkModule {
//
//    @Provides
//    @Singleton
//    fun provideOkHttpClient(): OkHttpClient {
//        return OkHttpClient.Builder()
//            .addInterceptor(HttpRequestInterceptor())
//            .build()
//    }
//
//    @Provides
//    @Singleton
//    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
//        return Retrofit.Builder()
//            .client(okHttpClient)
//            .baseUrl("https://api.twitter.com")
//            .addConverterFactory(GsonConverterFactory.create())
//            .addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
//            .build()
//    }
//
//    @Provides
//    @Singleton
//    fun provideTwitterApiService(retrofit: Retrofit): TwitterApiService {
//        return retrofit.create(TwitterApiService::class.java)
//    }
//
//    @Provides
//    @Singleton
//    fun provideTwitterAuthService(retrofit: Retrofit): TwitterAuthService {
//        return retrofit.create(TwitterAuthService::class.java)
//    }
//}
//
//internal class HttpRequestInterceptor : Interceptor {
//    override fun intercept(chain: Interceptor.Chain): Response {
//        val originalRequest = chain.request()
//        val request = originalRequest
//            .newBuilder()
//            .url(originalRequest.url)
//            .headers(originalRequest.headers)
//            .addHeader("User-Agent", "CrumbsTwitter")
//            .addHeader("Accept", "application/json")
//            .build()
//        Timber.d(request.toString())
//        Timber.d(request.headers.toString())
//        return chain.proceed(request)
//    }
//}
