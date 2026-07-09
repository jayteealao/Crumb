package com.github.jayteealao.crumbs.screens

import androidx.lifecycle.ViewModel
import com.github.jayteealao.crumbs.auth.AuthGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
     * Initialised synchronously from [AuthGateway.currentUser] so the first
     * collected value is accurate before any listener fires.
     */
    val isSignedIn: StateFlow<Boolean> = authGateway.currentUser
        .map { it != null }
        .stateIn(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
            initialValue = authGateway.currentUser.value != null,
        )
}
