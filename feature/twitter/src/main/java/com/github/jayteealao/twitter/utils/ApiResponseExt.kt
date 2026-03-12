package com.github.jayteealao.crumbs.utils

import com.github.jayteealao.twitter.models.SuccessMapper
import com.github.jayteealao.twitter.models.TweetResponse
import com.github.jayteealao.twitter.models.TweetResponseEntities
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
        Timber.d("called to produce with latestId $latestIdInDb")
        do {
            Timber.tag("token check")
            Timber.d("called with token: $token")
            call(token).suspendOnSuccess(SuccessMapper) {
                val hasKnownId = !latestIdInDb.isNullOrBlank() &&
                    data.any { it.tweetEntity.id == latestIdInDb }

                val itemsToSend = if (hasKnownId) {
                    data.takeWhile { it.tweetEntity.id != latestIdInDb }
                } else {
                    data
                }

                if (itemsToSend.isNotEmpty()) {
                    send(element = TweetResponseEntities(data = itemsToSend, meta = meta))
                }

                if (hasKnownId) {
                    Timber.d("Found known bookmark, stopping pagination")
                    this@produce.close()
                } else {
                    token = meta.nextToken
                    Timber.tag("token check")
                    Timber.d("next token: $token")
                }
            }.suspendOnError {
                if (response.code() in 401..404) {
                    Timber.d("refreshing token, old: $refreshToken")
                    onError()
                }

                if (response.code() == 429) {
                    this@produce.close()
                }
                Timber.d(this.message())
            }.onException {
                Timber.d(this.message())
            }.onFailure {
                Timber.d(this.message())
            }.getOrNull()
        } while (token != null)
    }
