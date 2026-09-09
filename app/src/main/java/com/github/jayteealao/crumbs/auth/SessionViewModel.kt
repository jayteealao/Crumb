package com.github.jayteealao.crumbs.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Exposes Firebase Auth sign-in state as a [StateFlow] for non-Login screens
 * ([HomeRoute], [AllBookmarksRoute], [SplashRoute]).
 *
 * The signal is seeded synchronously from [AuthGateway.currentUser] so warm-start
 * reads are immediately correct without a frame delay (Splash parity, per PO Q4).
 */
@HiltViewModel
class SessionViewModel
    @Inject
    constructor(
        authGateway: AuthGateway,
    ) : ViewModel() {
        /**
         * `true` when a Firebase Auth session is active; `false` when signed out.
         *
         * Initialised synchronously from [AuthGateway.currentUser] so the first
         * collected value is accurate before any listener fires.
         * The [viewModelScope] ensures collection stops when the ViewModel is cleared.
         */
        val isSignedIn: StateFlow<Boolean> =
            authGateway.currentUser
                .map { it != null }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    initialValue = authGateway.currentUser.value != null,
                )
    }
