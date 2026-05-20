package com.github.jayteealao.crumbs.auth

import java.util.ArrayDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Scriptable fake driven by a Deque of AuthResults. Tests push the sequence of
// expected results before calling the VM; each gateway call consumes one. The
// fake also exposes a CurrentUser flow so VM init flows can be exercised.
class FakeAuthGateway(
    initialUser: CurrentUser? = null,
) : AuthGateway {
    private val _currentUser = MutableStateFlow(initialUser)
    override val currentUser: StateFlow<CurrentUser?> = _currentUser.asStateFlow()

    private val googleResults = ArrayDeque<AuthResult>()
    private val emailResults = ArrayDeque<AuthResult>()
    private val linkResults = ArrayDeque<AuthResult>()

    // Auto-populated user that gets emitted on a successful sign-in unless the
    // test stubs a different value.
    var userOnSuccess: CurrentUser = CurrentUser(uid = "test-uid", email = "test@example.com")

    var signOutCallCount = 0
        private set

    fun queueGoogleResult(result: AuthResult) {
        googleResults.add(result)
    }

    fun queueEmailResult(result: AuthResult) {
        emailResults.add(result)
    }

    fun queueLinkResult(result: AuthResult) {
        linkResults.add(result)
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): AuthResult {
        val result = googleResults.pollFirst() ?: AuthResult.Unknown(IllegalStateException("no google result queued"))
        if (result is AuthResult.Success) _currentUser.value = userOnSuccess
        return result
    }

    override suspend fun signInWithEmailPassword(email: String, password: String): AuthResult {
        val result = emailResults.pollFirst() ?: AuthResult.Unknown(IllegalStateException("no email result queued"))
        if (result is AuthResult.Success) _currentUser.value = userOnSuccess
        return result
    }

    override suspend fun linkGoogleToCurrentUser(idToken: String): AuthResult {
        return linkResults.pollFirst() ?: AuthResult.Unknown(IllegalStateException("no link result queued"))
    }

    override suspend fun signOut() {
        signOutCallCount++
        _currentUser.value = null
    }
}
