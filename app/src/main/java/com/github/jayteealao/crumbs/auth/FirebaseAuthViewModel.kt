package com.github.jayteealao.crumbs.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class FirebaseAuthViewModel @Inject constructor(
    private val gateway: AuthGateway,
    private val coordinator: CredentialManagerCoordinator,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.SignedOut)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            gateway.currentUser.collect { user ->
                if (user != null) {
                    _uiState.value = AuthUiState.Authenticated(user.uid, user.email)
                } else if (_uiState.value is AuthUiState.Authenticated) {
                    _uiState.value = AuthUiState.SignedOut
                }
            }
        }
    }

    fun onGoogleSignInClicked(activity: Activity) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.SigningIn
            when (val result = coordinator.signInWithGoogle(activity)) {
                AuthResult.Success -> {
                    // currentUser flow will promote to Authenticated.
                }
                is AuthResult.CollisionRequiresEmail ->
                    _uiState.value = AuthUiState.CollisionRequiresEmailLink(result.pendingGoogleIdToken)
                AuthResult.InvalidCredentials ->
                    _uiState.value = AuthUiState.Error("Incorrect email or password. Please try again.")
                AuthResult.NetworkError ->
                    _uiState.value = AuthUiState.Error("Connection failed. Please check your internet connection and try again.")
                is AuthResult.Unknown -> {
                    Timber.e(result.cause, "Google sign-in failed")
                    _uiState.value = AuthUiState.Error("Sign in failed. Please try again or contact support if the problem continues.")
                }
            }
        }
    }

    fun onSignInWithEmailClicked() {
        // Manual entry path — surface the dialog without a pending Google token.
        if (_uiState.value !is AuthUiState.CollisionRequiresEmailLink) {
            _uiState.value = AuthUiState.EmailPasswordEntry
        }
    }

    fun onEmailPasswordSubmit(email: String, password: String) {
        viewModelScope.launch {
            val pendingToken = (_uiState.value as? AuthUiState.CollisionRequiresEmailLink)
                ?.pendingGoogleIdToken
            _uiState.value = AuthUiState.SigningIn
            when (val signIn = gateway.signInWithEmailPassword(email, password)) {
                AuthResult.Success -> {
                    if (pendingToken != null && pendingToken.isNotEmpty()) {
                        // Recover from the collision: link the Google credential
                        // onto the now-authenticated E/P account so the user has
                        // a single auth identity going forward.
                        when (val link = gateway.linkGoogleToCurrentUser(pendingToken)) {
                            AuthResult.Success -> { /* currentUser flow promotes */ }
                            else -> {
                                Timber.w("Auto-link after collision failed: $link")
                                // Sign-in succeeded — user is authenticated even if
                                // the link did not. currentUser flow will promote.
                            }
                        }
                    }
                }
                AuthResult.InvalidCredentials ->
                    _uiState.value = AuthUiState.Error("Incorrect email or password. Please try again.")
                AuthResult.NetworkError ->
                    _uiState.value = AuthUiState.Error("Connection failed. Please check your internet connection and try again.")
                is AuthResult.CollisionRequiresEmail ->
                    _uiState.value = AuthUiState.Error("An account with this email already exists. Try signing in instead.")
                is AuthResult.Unknown -> {
                    Timber.e(signIn.cause, "Email/password sign-in failed")
                    _uiState.value = AuthUiState.Error("Sign in failed. Please try again or contact support if the problem continues.")
                }
            }
        }
    }

    fun onDismissCollision() {
        _uiState.value = AuthUiState.SignedOut
    }

    fun onSignOut() {
        viewModelScope.launch { gateway.signOut() }
    }
}
