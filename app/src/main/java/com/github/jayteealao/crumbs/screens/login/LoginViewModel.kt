package com.github.jayteealao.crumbs.screens.login

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jayteealao.crumbs.data.AuthRepository
import com.github.jayteealao.crumbs.models.twitter.TokenResponse
import com.github.jayteealao.crumbs.models.twitter.TwitterUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private var _isAccessTokenAvailable: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isAccessTokenAvailable: StateFlow<Boolean>
        get() = _isAccessTokenAvailable

    val user: StateFlow<TwitterUser?>
        get() = authRepository.user

    var refreshedTokens = false

    init {
        viewModelScope.launch {
            authRepository.isAccessTokenAvailable.collect {
                _isAccessTokenAvailable.value = it
            }
        }
    }

    fun getAccessToken(authorizationCode: String) {
        viewModelScope.launch {
            authRepository.getAccess(authorizationCode = authorizationCode)
        }
    }

    suspend fun refreshToken(): Boolean {
        val refreshed = authRepository.refreshAccessToken()!!
        refreshedTokens = refreshed
        return refreshed
    }

    suspend fun revokeToken() = authRepository.revokeToken()

    fun authIntent(): Intent {
        return authRepository.getAuthorizationCodeIntent()
    }
}
