package com.github.jayteealao.crumbs.auth

import android.app.Activity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// VM is constructed directly with a FakeAuthGateway and a FakeCoordinator.
// CredentialManagerCoordinator is an interface so the test does not need to
// drive the real OS bottom sheet or R.string.default_web_client_id.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirebaseAuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var gateway: FakeAuthGateway
    private lateinit var coordinator: FakeCoordinator
    private lateinit var vm: FirebaseAuthViewModel
    private lateinit var activity: Activity

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        gateway = FakeAuthGateway()
        coordinator = FakeCoordinator(gateway)
        vm = FirebaseAuthViewModel(gateway, coordinator)
        activity = Robolectric.buildActivity(Activity::class.java).get()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun emailSignIn_success_landsAuthenticated() = runTest(dispatcher) {
        gateway.queueEmailResult(AuthResult.Success)

        vm.onEmailPasswordSubmit("user@example.com", "hunter2")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("expected Authenticated, got $state", state is AuthUiState.Authenticated)
    }

    @Test
    fun googleCollision_thenEmailSubmit_linksAndLandsAuthenticated() = runTest(dispatcher) {
        coordinator.nextGoogleResult = AuthResult.CollisionRequiresEmail("tok-123")
        vm.onGoogleSignInClicked(activity)
        advanceUntilIdle()

        val collisionState = vm.uiState.value
        assertTrue(
            "expected CollisionRequiresEmailLink, got $collisionState",
            collisionState is AuthUiState.CollisionRequiresEmailLink,
        )
        assertEquals(
            "tok-123",
            (collisionState as AuthUiState.CollisionRequiresEmailLink).pendingGoogleIdToken,
        )

        gateway.queueEmailResult(AuthResult.Success)
        gateway.queueLinkResult(AuthResult.Success)
        vm.onEmailPasswordSubmit("user@example.com", "hunter2")
        advanceUntilIdle()

        val finalState = vm.uiState.value
        assertTrue("expected Authenticated, got $finalState", finalState is AuthUiState.Authenticated)
    }

    @Test
    fun emailSignIn_networkError_landsError() = runTest(dispatcher) {
        gateway.queueEmailResult(AuthResult.NetworkError)

        vm.onEmailPasswordSubmit("user@example.com", "hunter2")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("expected Error, got $state", state is AuthUiState.Error)
    }
}

// Test-only coordinator that bypasses Credential Manager entirely. If the
// scripted result is Success, forwards to the gateway so the AuthStateListener
// path drives the VM's StateFlow exactly like production does.
private class FakeCoordinator(private val gateway: AuthGateway) : CredentialManagerCoordinator {
    var nextGoogleResult: AuthResult = AuthResult.Unknown(IllegalStateException("not scripted"))

    override suspend fun signInWithGoogle(activity: Activity): AuthResult {
        return if (nextGoogleResult is AuthResult.Success) {
            gateway.signInWithGoogleIdToken("test-token")
        } else {
            nextGoogleResult
        }
    }
}
