package com.github.jayteealao.crumbs.services

import com.github.jayteealao.crumbs.models.TweetResponse
import com.github.jayteealao.crumbs.models.TwitterUserResponse
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.getOrNull
import com.skydoves.sandwich.message
import com.skydoves.sandwich.onError
import com.skydoves.sandwich.onException
import com.skydoves.sandwich.onFailure
import com.skydoves.sandwich.suspendOnSuccess
import timber.log.Timber
import javax.inject.Inject

class TwitterApiClientImpl @Inject constructor(
    private val authPref: AuthPref,
    private val twitterApiService: TwitterApiService
) : TwitterApiClient {

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
