package com.github.jayteealao.reddit.data

import android.content.Context
import com.github.jayteealao.pref.readString
import com.github.jayteealao.pref.writeString
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferences wrapper for Reddit authentication tokens
 */
@Singleton
class RedditPrefs @Inject constructor(@ApplicationContext val context: Context) {

    val accessToken: Flow<String> = context.readString(ACCESS_TOKEN)
    val refreshToken: Flow<String> = context.readString(REFRESH_TOKEN)
    val username: Flow<String> = context.readString(USERNAME)

    suspend fun saveAccessToken(token: String) {
        context.writeString(ACCESS_TOKEN, token)
    }

    suspend fun saveRefreshToken(token: String) {
        context.writeString(REFRESH_TOKEN, token)
    }

    suspend fun saveUsername(username: String) {
        context.writeString(USERNAME, username)
    }

    suspend fun clearTokens() {
        context.writeString(ACCESS_TOKEN, "")
        context.writeString(REFRESH_TOKEN, "")
        context.writeString(USERNAME, "")
    }

    companion object {
        private const val ACCESS_TOKEN = "reddit_access_token"
        private const val REFRESH_TOKEN = "reddit_refresh_token"
        private const val USERNAME = "reddit_username"
    }
}
