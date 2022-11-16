package com.github.jayteealao.crumbs.services.twitter

import android.content.Intent
import com.github.jayteealao.crumbs.models.twitter.TokenResponse

interface TwitterAuthClient {
    fun authIntent(): Intent?

    suspend fun getAccessToken(authorizationCode: String): TokenResponse?

    suspend fun getAppOnlyAccessToken(): TokenResponse?

    suspend fun refreshAccessToken(refreshToken: String): TokenResponse?

    suspend fun revokeToken(accessToken: String): TokenResponse?
}
