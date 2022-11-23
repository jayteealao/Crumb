package com.github.jayteealao.twitter.data

import android.content.Context
import com.github.jayteealao.pref.readString
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

    val accessCode = context.readString(ACCESS_CODE)
    val refreshCode = context.readString(REFRESH_CODE)
    val appAccessCode = context.readString(APP_ACCESS_CODE)
    val appRefreshCode = context.readString(APP_REFRESH_CODE)
    val userId = context.readString(USERID)
    val username = context.readString(USERNAME)

    suspend fun setAccessAndRefreshToken(accessCode: String, refreshCode: String) {
        context.writeString(ACCESS_CODE, accessCode)
        context.writeString(REFRESH_CODE, refreshCode)
    }

    suspend fun setAccessCodeAppOnly(accessCode: String) {
        context.writeString(APP_ACCESS_CODE, accessCode)
    }

    suspend fun setUserId(userId: String) {
        context.writeString(USERID, userId)
    }

    suspend fun setUserName(userName: String) {
        context.writeString(USERNAME, userName)

    }
}