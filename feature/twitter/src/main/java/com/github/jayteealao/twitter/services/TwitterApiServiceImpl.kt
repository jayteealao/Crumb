package com.github.jayteealao.twitter.services

import com.github.jayteealao.twitter.data.Prefs
import com.github.jayteealao.twitter.models.TweetResponse
import com.github.jayteealao.twitter.models.TwitterUserResponse
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.getOrNull
import com.skydoves.sandwich.mapSuccess
import com.skydoves.sandwich.message
import com.skydoves.sandwich.onError
import com.skydoves.sandwich.onException
import com.skydoves.sandwich.onFailure
import com.skydoves.sandwich.suspendOnSuccess
import timber.log.Timber
import javax.inject.Inject

class TwitterApiClientImpl @Inject constructor(
    private val authPref: Prefs,
    private val twitterApiService: TwitterApiService
) : TwitterApiClient {

    override suspend fun getBookmarks(
        authorization: String,
        id: String,
        paginationToken: String?
    ): ApiResponse<TweetResponse> {
        Timber.d("getBookmarks called in client, paginationToken: $paginationToken")
        return twitterApiService.listBookmarks(
            authorization = authorization,
            userId = id,
            paginationToken = paginationToken
        )
    }

    override suspend fun getTweetThread(
        authorization: String,
        userId: String,
        conversationId: String,
        paginationToken: String?
    ): ApiResponse<TweetResponse> {
        return twitterApiService.getTweetThread(
            authorization,
            query = "conversation_id:$conversationId from:$userId to:$userId",
            paginationToken = paginationToken
        )
    }

    override suspend fun getTweetThread2(
        authorization: String,
        userId: String,
        conversationId: String,
        paginationToken: String?
    ): ApiResponse<TweetResponse> {
        return twitterApiService.getTweetThread2(
            authorization,
            userId = userId,
            paginationToken = paginationToken
        ).mapSuccess {
            this.copy(
                data = this.data.filter { it.conversationId == conversationId }
            )
        }
    }

    override suspend fun getUser(accessCode: String): TwitterUserResponse? {
        return twitterApiService.getUser("Bearer $accessCode").suspendOnSuccess {
            Timber.d("userRawData: ${response.raw().body}")
            Timber.d("userid: ${data.data.id} ${data.data.name} ${data.data.username}")
//            authPref.setUserId(data.data.id)
//            authPref.setUserName(data.data.username)
        }.onError {
            Timber.d("$response")
        }.onException {
            Timber.d(message())
        }.onFailure {
            Timber.d(message())
        }.getOrNull()
    }
}
