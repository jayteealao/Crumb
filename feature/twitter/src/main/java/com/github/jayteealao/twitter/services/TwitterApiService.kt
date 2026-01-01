package com.github.jayteealao.twitter.services

import com.github.jayteealao.twitter.models.TweetResponse
import com.github.jayteealao.twitter.models.TwitterUserResponse
import com.skydoves.sandwich.ApiResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface TwitterApiClient {

//    fun createApiInstance(credentials: TwitterCredentialsOAuth2)

//    fun createService()

    suspend fun getUser(accessCode: String): TwitterUserResponse?

//    suspend fun refreshAccessToken(refreshToken: String): ApiResponse<TokenResponse>?
    suspend fun getBookmarks(
        authorization: String,
        id: String,
        paginationToken: String? = null
    ): ApiResponse<TweetResponse>

    suspend fun getTweetThread(
        authorization: String,
        userId: String,
        conversationId: String,
        paginationToken: String? = null
    ): ApiResponse<TweetResponse>

    suspend fun getTweetThread2(
        authorization: String,
        userId: String,
        conversationId: String,
        paginationToken: String? = null
    ): ApiResponse<TweetResponse>
}

interface TwitterApiService {

    @GET("2/users/me")
    suspend fun getUser(
        @Header("Authorization") authorization: String
//        @Query("user.fields") userFields: String = "id,name,username"
    ): ApiResponse<TwitterUserResponse>

    @GET("2/users/{userId}/bookmarks")
    suspend fun listBookmarks(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String,
        @Query("tweet.fields") tweetFields: String = TWEETFIELDS,
        @Query("expansions") expansions: String = EXPANSIONS,
        @Query("media.fields") mediaFields: String = MEDIAFIELDS,
        @Query("user.fields") userFields: String = USERFIELDS,
        @Query("max_results") maxResults: Int = 100,
        @Query("pagination_token") paginationToken: String? = null
    ): ApiResponse<TweetResponse>

    @GET("2/tweets/search/recent")
    suspend fun getTweetThread(
        @Header("Authorization") authorization: String,
        @Query("query") query: String,
        @Query("tweet.fields") tweetFields: String = TWEETFIELDS,
        @Query("expansions") expansions: String = EXPANSIONS,
        @Query("media.fields") mediaFields: String = MEDIAFIELDS,
        @Query("user.fields") userFields: String = USERFIELDS,
        @Query("max_results") maxResults: Int = 100,
        @Query("pagination_token") paginationToken: String? = null
    ): ApiResponse<TweetResponse>

    @GET("2/users/{userId}/tweets")
    suspend fun getTweetThread2(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String,
        @Query("tweet.fields") tweetFields: String = TWEETFIELDS,
        @Query("expansions") expansions: String = EXPANSIONS,
        @Query("media.fields") mediaFields: String = MEDIAFIELDS,
        @Query("user.fields") userFields: String = USERFIELDS,
        @Query("max_results") maxResults: Int = 100,
        @Query("pagination_token") paginationToken: String? = null
    ): ApiResponse<TweetResponse>

//    @Headers("Content-Type: application/x-www-form-urlencoded")
//    @FormUrlEncoded
//    @POST("2/oauth2/token")
//    suspend fun refreshToken(
//        @Field("refresh_token") refreshToken: String,
//        @Field("grant_type") grantType: String = "refresh_token",
//        @Field("client_id") clientId: String = CLIENT_ID
//    ): ApiResponse<TokenResponse>
}

const val TWEETFIELDS = "id,in_reply_to_user_id,lang,entities," +
    "created_at,attachments,author_id,context_annotations,conversation_id," +
    "public_metrics,referenced_tweets,text,edit_history_tweet_ids,edit_controls," +
    "note_tweet,reply_settings,possibly_sensitive"
const val EXPANSIONS = "attachments.media_keys,attachments.poll_ids,author_id," +
    "entities.mentions.username,in_reply_to_user_id,referenced_tweets.id," +
    "referenced_tweets.id.author_id,edit_history_tweet_ids"
const val MEDIAFIELDS = "alt_text,media_key,url,type,public_metrics," +
    "preview_image_url,height,duration_ms,width,variants"
const val USERFIELDS = "id,profile_image_url,name,username,verified,verified_type," +
    "description,created_at,location"
