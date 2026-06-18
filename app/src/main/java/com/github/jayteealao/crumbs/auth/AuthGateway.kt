package com.github.jayteealao.crumbs.auth

import kotlinx.coroutines.flow.StateFlow

// Plain-data projection of FirebaseUser. Keeps FirebaseUser references at the
// gateway boundary — Robolectric cannot construct a real FirebaseUser, so the
// fake gateway populates this directly.
data class CurrentUser(
    val uid: String,
    val email: String?,
)

sealed class AuthResult {
    object Success : AuthResult()
    // Google Sign-In hit an existing Email/Password account. The pending Google
    // id token is held so the UI can prompt for E/P sign-in, then link.
    data class CollisionRequiresEmail(val pendingGoogleIdToken: String) : AuthResult()
    object InvalidCredentials : AuthResult()
    object NetworkError : AuthResult()
    data class Unknown(val cause: Throwable) : AuthResult()
}

sealed class AuthUiState {
    object SignedOut : AuthUiState()
    object SigningIn : AuthUiState()
    data class CollisionRequiresEmailLink(val pendingGoogleIdToken: String) : AuthUiState()
    object EmailPasswordEntry : AuthUiState()
    data class Authenticated(val uid: String, val email: String?) : AuthUiState()
    data class Error(val reason: String) : AuthUiState()
}

interface AuthGateway {
    val currentUser: StateFlow<CurrentUser?>

    // Called once by CrumbApplication.onCreate() to register the Firebase Auth
    // state listener. Keeping the side effect out of the constructor makes the
    // gateway testable without requiring listener-capture boilerplate.
    fun initialize()

    suspend fun signInWithGoogleIdToken(idToken: String): AuthResult

    suspend fun signInWithEmailPassword(email: String, password: String): AuthResult

    // Requires currentUser != null; used after collision recovery to merge the
    // pending Google credential onto the now-authenticated E/P account.
    suspend fun linkGoogleToCurrentUser(idToken: String): AuthResult

    suspend fun signOut()
}
