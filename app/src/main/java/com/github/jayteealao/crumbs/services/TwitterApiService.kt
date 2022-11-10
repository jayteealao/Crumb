package com.github.jayteealao.crumbs.services

import com.github.jayteealao.crumbs.models.TweetResponse
import com.github.jayteealao.crumbs.models.TwitterUserResponse
import com.skydoves.sandwich.ApiResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface TwitterApiClient {

    suspend fun getUser(accessCode: String): TwitterUserResponse?

}

interface TwitterApiService {

    @GET("2/users/me")
    suspend fun getUser(
        @Header("Authorization") authorization: String
    ): ApiResponse<TwitterUserResponse>

}
