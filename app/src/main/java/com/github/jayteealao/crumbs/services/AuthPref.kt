package com.github.jayteealao.crumbs.services

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import javax.inject.Inject

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class AuthPref @Inject constructor(@ApplicationContext val context: Context) {

    private val ACCESS_CODE = stringPreferencesKey("accessCode")

    val accessCode = context.dataStore.data.map { prefs ->
        prefs[ACCESS_CODE] ?: ""
    }

    suspend fun setAccessCode(accessCode: String) {
        context.dataStore.edit { prefs ->
            prefs[ACCESS_CODE] = accessCode
        }
    }

    private val REFRESH_CODE = stringPreferencesKey("refreshCode")

    val refreshCode = context.dataStore.data.map { prefs ->
        prefs[REFRESH_CODE] ?: ""
    }

    suspend fun setRefreshCode(refreshCode: String) {
        context.dataStore.edit { prefs ->
            prefs[REFRESH_CODE] = refreshCode
        }
    }

    suspend fun setAccessAndRefreshToken(accessCode: String, refreshCode: String) {
        context.dataStore.edit { prefs ->
            prefs[ACCESS_CODE] = accessCode
            prefs[REFRESH_CODE] = refreshCode
        }
    }

    private val USERID = stringPreferencesKey("userId")

    val userId = context.dataStore.data.map { prefs ->
        prefs[USERID] ?: ""
    }

    suspend fun setUserId(userId: String) {
        context.dataStore.edit { prefs ->
            prefs[USERID] = userId
        }
    }

    private val USERNAME = stringPreferencesKey("userName")

    val userName = context.dataStore.data.map { prefs ->
        prefs[USERNAME]
    }

    suspend fun setUserName(userName: String) {
        context.dataStore.edit { prefs ->
            prefs[USERNAME] = userName
        }
    }
}
