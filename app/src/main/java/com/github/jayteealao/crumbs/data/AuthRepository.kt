package com.github.jayteealao.crumbs.data

import com.github.jayteealao.crumbs.models.twitter.TokenResponse
import com.github.jayteealao.crumbs.models.twitter.TwitterUser
import com.github.jayteealao.crumbs.services.AuthPref
import com.github.jayteealao.crumbs.services.twitter.TwitterApiClient
import com.github.jayteealao.crumbs.services.twitter.TwitterAuthClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    val authPref: AuthPref,
    private val twitterAuthClient: TwitterAuthClient,
    private val twitterApiClient: TwitterApiClient,
    private val scope: CoroutineScope
) {

    val isAccessTokenAvailable = authPref.accessCode.map { it.isNotBlank() }

    val isUserAvailable = authPref.userId.map { it.isNotBlank() }

    var user: MutableStateFlow<TwitterUser?> = MutableStateFlow(null)

    fun getAuthorizationCodeIntent() = twitterAuthClient.authIntent()!!

    suspend fun getUser(accessToken: String) = twitterApiClient.getUser(accessToken)

    suspend fun getAccess(authorizationCode: String) {
        combine(authPref.accessCode, authPref.refreshCode, authPref.userId) { access, refreshToken, userId ->
            Timber.d("get access with $access, $refreshToken, $user")
            if (access.isBlank()) {
                val tokenResponse = twitterAuthClient.getAccessToken(authorizationCode)
                Timber.d("token response: $tokenResponse")
                if (tokenResponse != null) {
                    authPref.setAccessAndRefreshToken(tokenResponse.accessToken, tokenResponse.refreshToken)
                } else {
                    Timber.d("token response: $tokenResponse")
                    Timber.d("emitted fail")
                }
            } else if (userId.isBlank() && access.isNotBlank()) {
                val userResp = twitterApiClient.getUser(access)
                Timber.d("user response: $userResp")
                if (userResp != null) {
                    user.value = userResp.data
                    authPref.setUserId(userResp.data.id)
                    authPref.setUserName(userResp.data.username)
                }
            }
        }.collect {}
    }

    suspend fun refreshAccessToken(): Boolean? {
        var bool: Boolean? = null
        val refreshToken = authPref.refreshCode.first()
        if (refreshToken.isNotBlank()) {
            val tokenResponse = twitterAuthClient.refreshAccessToken(refreshToken)
            Timber.d("token response: $tokenResponse")
            bool = if (tokenResponse != null) {
                authPref.setAccessAndRefreshToken(
                    tokenResponse.accessToken,
                    tokenResponse.refreshToken
                )
                true
            } else {
                false
            }
        }

        return bool
    }
}

sealed class AuthState {
    object NoAccess : AuthState()
    data class AccessGranted(val accessToken: String, val refreshToken: String) : AuthState()
    data class AccessRefreshed(val accessToken: String, val refreshToken: String) : AuthState()
    data class Failed(val message: String) : AuthState()
    data class RefreshFailed(val message: String) : AuthState()
    data class userIdSet(val userId: String, val name: String, val userName: String, val previewImageUrl: String) : AuthState()
}
