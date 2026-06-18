package com.github.jayteealao.twitter.data

import android.content.Context
import com.github.jayteealao.pref.migrateTokenToEncrypted
import com.github.jayteealao.pref.readEncryptedString
import com.github.jayteealao.pref.readString
import com.github.jayteealao.pref.writeEncryptedString
import com.github.jayteealao.pref.writeString
import com.github.jayteealao.twitter.utils.ACCESS_CODE
import com.github.jayteealao.twitter.utils.APP_ACCESS_CODE
import com.github.jayteealao.twitter.utils.APP_REFRESH_CODE
import com.github.jayteealao.twitter.utils.REFRESH_CODE
import com.github.jayteealao.twitter.utils.USERID
import com.github.jayteealao.twitter.utils.USERNAME
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class Prefs @Inject constructor(@ApplicationContext val context: Context) {

    // Token fields are read through the encrypted path.
    val accessCode = context.readEncryptedString(ACCESS_CODE)
    val refreshCode = context.readEncryptedString(REFRESH_CODE)
    val appAccessCode = context.readEncryptedString(APP_ACCESS_CODE)
    val appRefreshCode = context.readEncryptedString(APP_REFRESH_CODE)

    // Non-sensitive fields remain plaintext.
    val userId = context.readString(USERID)
    val username = context.readString(USERNAME)

    suspend fun setAccessAndRefreshToken(accessCode: String, refreshCode: String) {
        context.writeEncryptedString(ACCESS_CODE, accessCode)
        context.writeEncryptedString(REFRESH_CODE, refreshCode)
    }

    suspend fun setAccessCodeAppOnly(accessCode: String) {
        context.writeEncryptedString(APP_ACCESS_CODE, accessCode)
    }

    suspend fun setUserId(userId: String) {
        context.writeString(USERID, userId)
    }

    suspend fun setUserName(userName: String) {
        context.writeString(USERNAME, userName)
    }

    suspend fun clearAllTokens() {
        context.writeEncryptedString(ACCESS_CODE, "")
        context.writeEncryptedString(REFRESH_CODE, "")
        context.writeEncryptedString(APP_ACCESS_CODE, "")
        context.writeEncryptedString(APP_REFRESH_CODE, "")
        context.writeString(USERID, "")
        context.writeString(USERNAME, "")
    }

    /**
     * One-time migration: re-encrypts any token fields that were written in
     * plaintext by a previous app version. Safe to call on every app start;
     * already-encrypted fields are left untouched.
     */
    suspend fun migrateTokensToEncrypted() {
        context.migrateTokenToEncrypted(ACCESS_CODE)
        context.migrateTokenToEncrypted(REFRESH_CODE)
        context.migrateTokenToEncrypted(APP_ACCESS_CODE)
        context.migrateTokenToEncrypted(APP_REFRESH_CODE)
    }
}