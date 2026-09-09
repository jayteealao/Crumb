package com.github.jayteealao.crumbs.screens

import com.github.jayteealao.crumbs.auth.AuthUiState
import com.github.jayteealao.crumbs.screens.login.shouldAutoNavigate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the auto-navigation decision for the login screen. Every [AuthUiState]
 * subtype is covered exhaustively — if a new subtype is ever added to [AuthUiState],
 * the `when` in [shouldAutoNavigate] will fail to compile, forcing a conscious decision
 * about its navigation semantics.
 *
 * Note: the legacy Twitter token has no influence over this function by design.
 * AC6's grep (`grep -rn "loginViewModel.isAccessTokenAvailable" app/src/main`)
 * is the structural enforcement of the legacy retirement, not a test assertion here.
 */
class LoginAutoNavTest {
    @Test
    fun authenticated_shouldAutoNavigate_returnsTrue() {
        assertTrue(shouldAutoNavigate(AuthUiState.Authenticated(uid = "uid", email = "e@g.com")))
    }

    @Test
    fun signedOut_shouldAutoNavigate_returnsFalse() {
        assertFalse(shouldAutoNavigate(AuthUiState.SignedOut))
    }

    @Test
    fun signingIn_shouldAutoNavigate_returnsFalse() {
        // SigningIn suppression is the belt-and-braces guard: no navigation fires
        // while an in-flight Credential Manager coroutine is suspended in getCredential(),
        // preventing the OS sheet from being cancelled by a composable pop.
        assertFalse(shouldAutoNavigate(AuthUiState.SigningIn))
    }

    @Test
    fun collision_shouldAutoNavigate_returnsFalse() {
        assertFalse(shouldAutoNavigate(AuthUiState.CollisionRequiresEmailLink("pending-tok")))
    }

    @Test
    fun emailPasswordEntry_shouldAutoNavigate_returnsFalse() {
        assertFalse(shouldAutoNavigate(AuthUiState.EmailPasswordEntry))
    }

    @Test
    fun error_shouldAutoNavigate_returnsFalse() {
        assertFalse(shouldAutoNavigate(AuthUiState.Error("something went wrong")))
    }
}
