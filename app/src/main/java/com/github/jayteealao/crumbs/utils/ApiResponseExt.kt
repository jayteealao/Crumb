package com.github.jayteealao.crumbs.utils

import com.github.jayteealao.crumbs.models.SuccessMapper
import com.github.jayteealao.crumbs.models.TweetResponse
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.getOrNull
import com.skydoves.sandwich.message
import com.skydoves.sandwich.onException
import com.skydoves.sandwich.onFailure
import com.skydoves.sandwich.suspendOnError
import com.skydoves.sandwich.suspendOnSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.produce
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
fun CoroutineScope.produceTweetResponseEntities(
    refreshToken: String,
    latestIdInDb: String? = null,
    onError: suspend () -> Unit = {},
    call: suspend (paginationToken: String?) -> ApiResponse<TweetResponse>
) =
    produce {
        var token: String? = null
        var retries = 0
        Timber.d("called to produce with latestId $latestIdInDb")
        do {
            if (token == "retry") { token = null }
            Timber.tag("token check")
            Timber.d("called with token: $token")
            call(token).suspendOnSuccess(SuccessMapper) {
                send(element = this)
                token = meta.nextToken
                Timber.tag("token check")
                Timber.d("next token: $token")
                if (!latestIdInDb.isNullOrBlank() && data.any { it.tweetEntity.id == latestIdInDb }) {
                    Timber.tag("producer closed")
                    Timber.d("closed here")
                    this@produce.close()
                }
            }.suspendOnError {
                if (response.code() in 401..404) {
                    Timber.d("refreshing token, old: $refreshToken")
                    onError()
//                    val resp = twitterApiClient.refreshAccessToken(refreshToken)
//                    Timber.d("refreshedToken: ${resp?.getOrNull()?.refreshToken}")
                }
//                if (retries > 3) {
//                    token = null
//                    Timber.d("retrying failed")
//
//                } else {
//                    retries += 1
//                    token = "retry"
//                    Timber.d("retrying $retries")
//                }
                Timber.d(this.message())
            }.onException {
                Timber.d(this.message())
            }.onFailure {
                Timber.d(this.message())
            }.getOrNull()
        } while (token != null)
    }
