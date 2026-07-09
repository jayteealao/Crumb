package com.github.jayteealao.crumbs.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jayteealao.crumbs.auth.AuthGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Exposes Firebase Auth sign-in state as a [StateFlow] for [SplashRoute].
 *
 * Keying the splash routing decision on the live Firebase Auth session rather
 * than the legacy local Twitter token means a signed-out user is always sent
 * to Login — even when a stale X token remains in Prefs.
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    authGateway: AuthGateway,
) : ViewModel() {

    /**
     * `true` when a Firebase Auth session exists; `false` when signed out.
     *
     * Initialised synchronously from [AuthGateway.currentUser] so the first
     * collected value is accurate before any listener fires.
     * The [viewModelScope] ensures collection stops when the ViewModel is cleared.
     */
    val isSignedIn: StateFlow<Boolean> = authGateway.currentUser
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = authGateway.currentUser.value != null,
        )
}
