package com.github.jayteealao.twitter.data

import com.github.jayteealao.twitter.models.TokenResponse
import com.github.jayteealao.twitter.models.TwitterUser
import com.github.jayteealao.twitter.services.TwitterApiClient
import com.github.jayteealao.twitter.services.TwitterAuthClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authPref: Prefs,
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
                    authPref.setAccessAndRefreshToken(tokenResponse.accessToken!!, tokenResponse.refreshToken!!)
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

    suspend fun getAppOnlyAccess(): TokenResponse? =
        twitterAuthClient.getAppOnlyAccessToken().also {
            Timber.d("$it")
            if (it != null) {
                authPref.setAccessCodeAppOnly(it.accessToken!!)
            }
        }
//    fun checkAuthStatus(): Flow<AuthState> = flow {
//        combine(authPref.accessCode, authPref.refreshCode) { access, refreshToken ->
//            if (access.isBlank()) {
//                emit(AuthState.NoAccess)
//            } else {
//                emit(AuthState.AccessGranted(access, refreshToken))
//            }
//        }.collect {}
//    }

    suspend fun refreshAccessToken(): Boolean {
        var bool = false
        val refreshToken = authPref.refreshCode.first()
        if (refreshToken.isNotBlank()) {
            val tokenResponse = twitterAuthClient.refreshAccessToken(refreshToken)
            Timber.d("token response: $tokenResponse")
            if (tokenResponse != null) {
                authPref.setAccessAndRefreshToken(
                    tokenResponse.accessToken!!,
                    tokenResponse.refreshToken!!
                )
                bool = true
            }
        }

        return bool
    }

    suspend fun revokeToken(): TokenResponse? =
        twitterAuthClient.revokeToken(authPref.accessCode.first()).also { data ->
            Timber.d("account revoked: $data")
//            if (data != null) { // is the data check necessary
                authPref.setAccessAndRefreshToken("", "")
//            }
//        }
    }
}
