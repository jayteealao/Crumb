package com.github.jayteealao.twitter.data

import com.github.jayteealao.twitter.models.TokenResponse
import com.github.jayteealao.twitter.models.TwitterUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Post-cutover: device no longer talks to api.x.com directly. OAuth runs
// through Cloud Functions (mintOAuthState/oauthCallback) and link state lives
// in Firestore sync_status. This class survives as a Prefs-backed compatibility
// surface for the legacy LoginViewModel callers — its auth methods are no-ops
// because the legacy access/refresh codes are cleared by the migration worker.
@Singleton
class AuthRepository @Inject constructor(
    private val authPref: Prefs,
) {

    val isAccessTokenAvailable = authPref.accessCode.map { it.isNotBlank() }

    val isUserAvailable = authPref.userId.map { it.isNotBlank() }

    var user: MutableStateFlow<TwitterUser?> = MutableStateFlow(null)

    suspend fun getAccess(authorizationCode: String) {
        // No-op: server-side OAuth (oauthCallback) handles code → refresh-token
        // exchange. Retained so the legacy LoginViewModel surface still resolves.
    }

    suspend fun refreshAccessToken(): Boolean = false

    suspend fun revokeToken(): TokenResponse? = null

    suspend fun logout() {
        authPref.clearAllTokens()
    }
}
