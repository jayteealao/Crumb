package com.github.jayteealao.twitter.services

import android.content.Intent
import com.github.jayteealao.twitter.models.TokenResponse

interface TwitterAuthClient {
    fun authIntent(): Intent?

    suspend fun getAccessToken(authorizationCode: String): TokenResponse?

    suspend fun getAppOnlyAccessToken(): TokenResponse?

    suspend fun refreshAccessToken(refreshToken: String): TokenResponse?

    suspend fun revokeToken(accessToken: String): TokenResponse?
}
