package com.github.jayteealao.crumbs.services

import com.github.jayteealao.crumbs.models.TokenResponse
import com.skydoves.sandwich.ApiResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Headers
import retrofit2.http.POST

interface TwitterAuthService {

    @Headers("Content-Type: application/x-www-form-urlencoded")
    @FormUrlEncoded
    @POST("2/oauth2/token")
    suspend fun getAccessToken(
        @Field("code") code: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("client_id") clientId: String = CLIENT_ID,
        @Field("redirect_uri") redirectUri: String = "https://graphitenerd.xyz",
        @Field("code_verifier") codeVerifier: String = "challenge"
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
        @Field("client_id") clientId: String = CLIENT_ID
    )
}
