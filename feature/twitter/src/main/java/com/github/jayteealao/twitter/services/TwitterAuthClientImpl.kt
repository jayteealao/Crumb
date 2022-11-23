package com.github.jayteealao.twitter.services

import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.github.jayteealao.twitter.models.TokenResponse
import com.skydoves.sandwich.getOrNull
import com.skydoves.sandwich.message
import com.skydoves.sandwich.onError
import com.skydoves.sandwich.onException
import com.skydoves.sandwich.onFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class TwitterAuthClientImpl @Inject constructor(
    private val twitterAuthService: TwitterAuthService,
    private val scope: CoroutineScope
) : TwitterAuthClient {

    private val authorizationUrl = "https://twitter.com/i/oauth2/authorize?response_type=code" +
        "&client_id=QnFuclQ0SGZIS01zVlZsdm5jU0o6MTpjaQ" +
        "&redirect_uri=https://graphitenerd.xyz" +
        "&scope=offline.access%20tweet.read%20users.read%20bookmark.read%20bookmark.write" +
        "&state=state&code_challenge=challenge&code_challenge_method=plain"

    override fun authIntent(): Intent {
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(authorizationUrl)
        }
        return Intent.createChooser(viewIntent, "Open Link with")
    }

    override suspend fun getAccessToken(authorizationCode: String): TokenResponse? {
        var result: TokenResponse? = null
        scope.launch {
            result = twitterAuthService
                .getAccessToken(authorizationCode).onError {
                    Timber.d("$response")
                }.onException {
                    Timber.d(message())
                }.onFailure {
                    Timber.d(message())
                }.getOrNull()
            Timber.tag("access token")
            Timber.d("token actual data: ${result?.accessToken}")
            Timber.d("token expires: ${result?.expiresIn}")
            Timber.d("token expires: ${result?.tokenType}")
//            authPref.setAccessCode(result?.accessToken ?: "")
//            authPref.setRefreshCode(result?.refreshToken ?: "")
        }.join()
        return result
    }

    override suspend fun getAppOnlyAccessToken(): TokenResponse? {
        var result: TokenResponse? = null
        scope.launch {
//            val result = service.getAccessToken(pkce, authorizationCode)
//            credentials.twitterOauth2AccessToken = result.accessToken
//            credentials.twitterOauth2RefreshToken = result.refreshToken
            result = twitterAuthService.getAppOnlyAccessToken(
                    AppOnlyBody(),
                    "Basic ${Base64.encodeToString("QnFuclQ0SGZIS01zVlZsdm5jU0o6MTpjaQ:r3KjJTwKRuhNrBDJgFI0SzkCQqYlf59H3CrgfBSDASda3Lc-MM".toByteArray(), Base64.NO_WRAP + Base64.URL_SAFE)}",
                ).onError {
                    Timber.d("$response")
                }.onException {
                    Timber.d(message())
                }.onFailure {
                    Timber.d(message())
                }.getOrNull()
            Timber.tag("access token")
            Timber.d("token actual data: ${result?.accessToken}")
            Timber.d("token expires: ${result?.expiresIn}")
            Timber.d("token expires: ${result?.tokenType}")
//            authPref.setAccessCode(result?.accessToken ?: "")
//            authPref.setRefreshCode(result?.refreshToken ?: "")
        }.join()
        return result
    }

    override suspend fun refreshAccessToken(refreshToken: String): TokenResponse? {
        var result: TokenResponse? = null
        scope.launch {
            result = twitterAuthService.refreshAccessToken(refreshToken).onError {
                Timber.d("$response")
            }.onException {
                Timber.d(message())
            }.onFailure {
                Timber.d(message())
            }.getOrNull()
        }.join()
        return result
    }

    override suspend fun revokeToken(accessToken: String): TokenResponse? {
        var result: TokenResponse? = null
        scope.launch {
            result = twitterAuthService.revokeAccessToken(accessToken).onError {
                Timber.d("$response")
                Timber.d(this.errorBody.toString())
            }.onException {
                Timber.d(message())
            }.onFailure {
                Timber.d(message())
            }.getOrNull()
        }.join()
        Timber.d("revoke token: $result")
        return result
    }
}
