package com.github.jayteealao.twitter.services

import com.github.jayteealao.twitter.models.TokenResponse
import com.github.jayteealao.twitter.utils.CLIENT_ID
import com.google.gson.annotations.SerializedName
import com.skydoves.sandwich.ApiResponse
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface TwitterAuthService {

    @Headers("Content-Type: application/x-www-form-urlencoded")
    @FormUrlEncoded
    @POST("2/oauth2/token")
    suspend fun getAccessToken(
        @Field("code") code: String,
        @Field("code_verifier") codeVerifier: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("client_id") clientId: String = CLIENT_ID,
        @Field("redirect_uri") redirectUri: String = "https://graphitenerd.xyz"
    ): ApiResponse<TokenResponse>

    @Headers("Content-Type: application/x-www-form-urlencoded;charset=UTF-8")
    @POST("2/oauth2/token")
    suspend fun getAppOnlyAccessToken(
        @Body grantType: AppOnlyBody,
        @Header("Authorization") authorization: String,
    ): ApiResponse<TokenResponse>

    @Headers("Content-Type: application/x-www-form-urlencoded")
    @FormUrlEncoded
    @POST("2/oauth2/token")
    suspend fun refreshAccessToken(
        @Field("refresh_token") refreshToken: String,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("client_id") clientId: String = CLIENT_ID
    ): ApiResponse<TokenResponse>

    @Headers("Content-Type: application/x-www-form-urlencoded")
    @FormUrlEncoded
    @POST("2/oauth2/revoke")
    suspend fun revokeAccessToken(
        @Field("token") accessToken: String,
        @Field("client_id") clientId: String = CLIENT_ID,
        @Field("token_type_hint") tokenTypeHint: String = "access_token"
    ): ApiResponse<TokenResponse>
}

data class AppOnlyBody(
    @SerializedName("grant_type") val grantType: String = "client_credentials"
        )

