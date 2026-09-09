package com.github.jayteealao.crumbs.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies the signed-in derivation in [SessionViewModel] against [FakeAuthGateway]:
 * null user → false; seeded user → true; emission flips the value.
 *
 * Robolectric-free: [SessionViewModel] has no Android SDK dependencies beyond the
 * main-dispatcher requirement of [viewModelScope].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun nullUserAtConstruction_isSignedIn_isFalse() =
        runTest(dispatcher) {
            val gateway = FakeAuthGateway(initialUser = null)
            val vm = SessionViewModel(gateway)
            assertFalse("expected false when no user present", vm.isSignedIn.value)
        }

    @Test
    fun seededUserAtConstruction_isSignedIn_isTrue() =
        runTest(dispatcher) {
            val gateway = FakeAuthGateway(initialUser = CurrentUser(uid = "uid-1", email = "a@b.com"))
            val vm = SessionViewModel(gateway)
            assertTrue("expected true when user is seeded at construction", vm.isSignedIn.value)
        }

    @Test
    fun signIn_flipsIsSignedInFromFalseToTrue() =
        runTest(dispatcher) {
            val gateway = FakeAuthGateway(initialUser = null)
            val vm = SessionViewModel(gateway)
            assertFalse("precondition: must start false", vm.isSignedIn.value)

            gateway.queueGoogleResult(AuthResult.Success)
            gateway.signInWithGoogleIdToken("test-id-token")
            advanceUntilIdle()

            assertTrue("expected true after successful sign-in emission", vm.isSignedIn.value)
        }
}
