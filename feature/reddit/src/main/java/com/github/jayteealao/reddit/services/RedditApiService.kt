package com.github.jayteealao.reddit.services

import com.github.jayteealao.reddit.models.RedditListingResponse
import com.github.jayteealao.reddit.models.RedditTokenResponse
import com.github.jayteealao.reddit.models.RedditUserResponse
import com.skydoves.sandwich.ApiResponse
import retrofit2.http.*

/**
 * Reddit API Service
 *
 * OAuth2 Documentation: https://github.com/reddit-archive/reddit/wiki/OAuth2
 * API Documentation: https://www.reddit.com/dev/api/
 */
interface RedditApiService {

    /**
     * Get current authenticated user
     */
    @GET("api/v1/me")
    suspend fun getUser(
        @Header("Authorization") authorization: String
    ): ApiResponse<RedditUserResponse>

    /**
     * Get saved posts for authenticated user
     *
     * @param authorization Bearer token
     * @param username Username (use "me" for authenticated user)
     * @param limit Number of items (1-100, default 25)
     * @param after Pagination token from previous response
     * @param before Pagination token (opposite of after)
     */
    @GET("user/{username}/saved")
    suspend fun getSavedPosts(
        @Header("Authorization") authorization: String,
        @Path("username") username: String = "me",
        @Query("limit") limit: Int = 100,
        @Query("after") after: String? = null,
        @Query("before") before: String? = null,
        @Query("show") show: String = "all", // Show all saved items
        @Query("raw_json") rawJson: Int = 1 // Return unescaped JSON
    ): ApiResponse<RedditListingResponse>

    companion object {
        const val BASE_URL = "https://oauth.reddit.com/"
        const val AUTH_BASE_URL = "https://www.reddit.com/"
    }
}

/**
 * Reddit OAuth Service (different base URL for auth endpoints)
 */
interface RedditOAuthService {

    /**
     * Exchange authorization code for access token
     *
     * @param grantType Must be "authorization_code"
     * @param code Authorization code from OAuth redirect
     * @param redirectUri Must match the one used in authorization request
     */
    @Headers("Content-Type: application/x-www-form-urlencoded")
    @FormUrlEncoded
    @POST("api/v1/access_token")
    suspend fun getAccessToken(
        @Header("Authorization") basicAuth: String, // Basic auth with client_id:client_secret
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String
    ): ApiResponse<RedditTokenResponse>

    /**
     * Refresh access token
     *
     * @param grantType Must be "refresh_token"
     * @param refreshToken The refresh token from initial auth
     */
    @Headers("Content-Type: application/x-www-form-urlencoded")
    @FormUrlEncoded
    @POST("api/v1/access_token")
    suspend fun refreshAccessToken(
        @Header("Authorization") basicAuth: String,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String
    ): ApiResponse<RedditTokenResponse>

    /**
     * Revoke access token (logout)
     *
     * @param token The access token to revoke
     * @param tokenTypeHint Either "access_token" or "refresh_token"
     */
    @Headers("Content-Type: application/x-www-form-urlencoded")
    @FormUrlEncoded
    @POST("api/v1/revoke_token")
    suspend fun revokeToken(
        @Header("Authorization") basicAuth: String,
        @Field("token") token: String,
        @Field("token_type_hint") tokenTypeHint: String = "access_token"
    ): ApiResponse<Unit>
}
