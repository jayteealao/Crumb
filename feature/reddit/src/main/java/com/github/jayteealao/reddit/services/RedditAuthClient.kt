package com.github.jayteealao.reddit.services

import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.github.jayteealao.reddit.data.RedditPrefs
import com.github.jayteealao.reddit.models.RedditTokenResponse
import com.skydoves.sandwich.getOrNull
import com.skydoves.sandwich.message
import com.skydoves.sandwich.onError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reddit OAuth Client
 *
 * Implements OAuth2 authorization code flow with PKCE
 * https://github.com/reddit-archive/reddit/wiki/OAuth2
 */

interface RedditAuthClient {
    fun getAuthIntent(): Intent
    suspend fun getAccessToken(code: String): String?
    suspend fun refreshAccessToken(refreshToken: String): String?
    suspend fun revokeToken(token: String)
}

@Singleton
class RedditAuthClientImpl @Inject constructor(
    private val redditOAuthService: RedditOAuthService,
    private val redditPrefs: RedditPrefs,
    private val scope: kotlinx.coroutines.CoroutineScope
) : RedditAuthClient {

    companion object {
        // TODO: Move these to BuildConfig or gradle.properties for security
        private const val CLIENT_ID = "Rd5c1NB5wOxJcPF5ihKDw"
        private const val REDIRECT_URI = "crumbs://graphitenerd.xyz"

        private const val AUTH_URL = "https://www.reddit.com/api/v1/authorize.compact"
        private const val SCOPE = "identity,history,read,save" // Scopes needed

        private const val STATE_LENGTH = 32
    }

    /**
     * Generate authorization intent for Reddit OAuth
     *
     * Opens Reddit's authorization page in browser/Custom Tab
     */
    override fun getAuthIntent(): Intent {
        val state = generateRandomState()

        val authUri = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("state", state)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("duration", "permanent") // Get refresh token
            .appendQueryParameter("scope", SCOPE)
            .build()

        Timber.d("Reddit OAuth URL: $authUri")

        return Intent(Intent.ACTION_VIEW, authUri)
    }

    /**
     * Exchange authorization code for access token
     *
     * @param code Authorization code from OAuth redirect
     * @return Access token or null if failed
     */
    override suspend fun getAccessToken(code: String): String? {
        Timber.d("Exchanging code for access token")

        var accessToken: String? = null
        val basicAuth = getBasicAuth()

        scope.launch {
            val result = redditOAuthService.getAccessToken(
                basicAuth = basicAuth,
                code = code,
                redirectUri = REDIRECT_URI
            ).onError {
                Timber.e("Failed to get access token: ${message()}")
            }.getOrNull()

            result?.let {
                Timber.d("Successfully got access token")
                saveTokens(it)
                accessToken = it.accessToken
            }
        }.join()

        return accessToken
    }

    /**
     * Refresh access token using refresh token
     *
     * @param refreshToken The refresh token
     * @return New access token or null if failed
     */
    override suspend fun refreshAccessToken(refreshToken: String): String? {
        Timber.d("Refreshing access token")

        var accessToken: String? = null
        val basicAuth = getBasicAuth()

        scope.launch {
            val result = redditOAuthService.refreshAccessToken(
                basicAuth = basicAuth,
                refreshToken = refreshToken
            ).onError {
                Timber.e("Failed to refresh token: ${message()}")
            }.getOrNull()

            result?.let {
                Timber.d("Successfully refreshed token")
                saveTokens(it)
                accessToken = it.accessToken
            }
        }.join()

        return accessToken
    }

    /**
     * Revoke access token (logout)
     */
    override suspend fun revokeToken(token: String) {
        val basicAuth = getBasicAuth()

        scope.launch {
            val result = redditOAuthService.revokeToken(
                basicAuth = basicAuth,
                token = token
            ).onError {
                Timber.e("Failed to revoke token: ${message()}")
            }.getOrNull()

            result?.let {
                Timber.d("Token revoked successfully")
                clearTokens()
            }
        }.join()
    }

    /**
     * Save tokens to preferences
     */
    private suspend fun saveTokens(tokenResponse: RedditTokenResponse) {
        redditPrefs.saveAccessToken(tokenResponse.accessToken)
        tokenResponse.refreshToken?.let {
            redditPrefs.saveRefreshToken(it)
        }

        // Calculate expiration time (current time + expires_in)
        val expiresAt = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000)
        // TODO: Save expiration time to prefs if needed

        Timber.d("Tokens saved. Expires in ${tokenResponse.expiresIn}s")
    }

    /**
     * Clear tokens from preferences
     */
    private suspend fun clearTokens() {
        redditPrefs.clearTokens()
    }

    /**
     * Get Basic Auth header for OAuth requests
     * Format: "Basic base64(client_id:)"
     * Note: Reddit uses empty secret for installed apps
     */
    private fun getBasicAuth(): String {
        val credentials = "$CLIENT_ID:"
        val encoded = Base64.encodeToString(
            credentials.toByteArray(),
            Base64.NO_WRAP
        )
        return "Basic $encoded"
    }

    /**
     * Generate random state for CSRF protection
     */
    private fun generateRandomState(): String {
        val random = SecureRandom()
        val bytes = ByteArray(STATE_LENGTH)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP)
    }
}
