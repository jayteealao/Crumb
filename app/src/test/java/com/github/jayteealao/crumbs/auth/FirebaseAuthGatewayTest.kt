package com.github.jayteealao.crumbs.auth

import android.content.Context
import com.github.jayteealao.twitter.data.TwitterSyncEnqueuer
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthResult as FirebaseAuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import dagger.Lazy
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [FirebaseAuthGateway] exception-to-[AuthResult] mapping.
 *
 * Strategy: mock [FirebaseAuth] so that each `signInWith*` / `linkWithCredential`
 * method returns a [com.google.android.gms.tasks.Task] produced by
 * [Tasks.forResult] (success) or [Tasks.forException] (failure). The gateway's
 * inline [runAuthCall] catches the exception that [kotlinx.coroutines.tasks.await]
 * re-throws and maps it to the correct domain type.
 *
 * Robolectric is required only because the [FirebaseAuth] SDK touches Android
 * internals during class loading. No Activity or UI is exercised.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirebaseAuthGatewayTest {

    private lateinit var auth: FirebaseAuth
    private lateinit var context: Context
    private lateinit var syncEnqueuer: Lazy<TwitterSyncEnqueuer>
    private lateinit var gateway: FirebaseAuthGateway

    @Before
    fun setUp() {
        // The auth state listener is registered only when initialize() is called
        // (M-03: side effect moved out of init block). Tests that don't call
        // initialize() need no listener-capture boilerplate; relaxed = true
        // silently ignores any addAuthStateListener call if a test does trigger it.
        auth = mockk(relaxed = true) {
            every { currentUser } returns null
        }
        context = mockk(relaxed = true)
        syncEnqueuer = Lazy { mockk(relaxed = true) }
        gateway = FirebaseAuthGateway(auth, context, syncEnqueuer)
    }

    // -------------------------------------------------------------------------
    // signInWithEmailPassword
    // -------------------------------------------------------------------------

    @Test
    fun `signInWithEmailPassword success returns AuthResult Success`() = runTest {
        val task = Tasks.forResult(mockk<FirebaseAuthResult>(relaxed = true))
        every { auth.signInWithEmailAndPassword(any(), any()) } returns task

        val result = gateway.signInWithEmailPassword("user@example.com", "correct-password")

        assertEquals(AuthResult.Success, result)
    }

    @Test
    fun `signInWithEmailPassword FirebaseAuthInvalidCredentialsException maps to InvalidCredentials`() =
        runTest {
            val ex = FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "Wrong password")
            every { auth.signInWithEmailAndPassword(any(), any()) } returns Tasks.forException(ex)

            val result = gateway.signInWithEmailPassword("user@example.com", "wrong-password")

            assertEquals(AuthResult.InvalidCredentials, result)
        }

    @Test
    fun `signInWithEmailPassword FirebaseNetworkException maps to NetworkError`() = runTest {
        val ex = FirebaseNetworkException("Network error")
        every { auth.signInWithEmailAndPassword(any(), any()) } returns Tasks.forException(ex)

        val result = gateway.signInWithEmailPassword("user@example.com", "password")

        assertEquals(AuthResult.NetworkError, result)
    }

    @Test
    fun `signInWithEmailPassword unknown exception maps to Unknown`() = runTest {
        val ex = RuntimeException("Something unexpected")
        every { auth.signInWithEmailAndPassword(any(), any()) } returns Tasks.forException(ex)

        val result = gateway.signInWithEmailPassword("user@example.com", "password")

        assertTrue("expected Unknown, got $result", result is AuthResult.Unknown)
        assertEquals(ex, (result as AuthResult.Unknown).cause)
    }

    @Test
    fun `signInWithEmailPassword collision is NOT allowed - maps to Unknown`() = runTest {
        val ex = FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "Account exists")
        every { auth.signInWithEmailAndPassword(any(), any()) } returns Tasks.forException(ex)

        val result = gateway.signInWithEmailPassword("user@example.com", "password")

        // allowCollision = false for email/password path → Unknown
        assertTrue("expected Unknown for collision, got $result", result is AuthResult.Unknown)
        assertEquals(ex, (result as AuthResult.Unknown).cause)
    }

    // -------------------------------------------------------------------------
    // signInWithGoogleIdToken
    // -------------------------------------------------------------------------

    @Test
    fun `signInWithGoogleIdToken success returns AuthResult Success`() = runTest {
        val task = Tasks.forResult(mockk<FirebaseAuthResult>(relaxed = true))
        every { auth.signInWithCredential(any()) } returns task

        val result = gateway.signInWithGoogleIdToken("id-token-123")

        assertEquals(AuthResult.Success, result)
    }

    @Test
    fun `signInWithGoogleIdToken collision allowed - maps to CollisionRequiresEmail with original token`() =
        runTest {
            val ex = FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "Account exists")
            every { auth.signInWithCredential(any()) } returns Tasks.forException(ex)

            val result = gateway.signInWithGoogleIdToken("my-google-id-token")

            assertTrue(
                "expected CollisionRequiresEmail, got $result",
                result is AuthResult.CollisionRequiresEmail,
            )
            // The gateway replaces the placeholder empty token with the original idToken.
            assertEquals("my-google-id-token", (result as AuthResult.CollisionRequiresEmail).pendingGoogleIdToken)
        }

    @Test
    fun `signInWithGoogleIdToken FirebaseAuthInvalidCredentialsException maps to InvalidCredentials`() =
        runTest {
            val ex = FirebaseAuthInvalidCredentialsException("ERROR_INVALID_ID_TOKEN", "Bad token")
            every { auth.signInWithCredential(any()) } returns Tasks.forException(ex)

            val result = gateway.signInWithGoogleIdToken("bad-token")

            assertEquals(AuthResult.InvalidCredentials, result)
        }

    @Test
    fun `signInWithGoogleIdToken FirebaseNetworkException maps to NetworkError`() = runTest {
        val ex = FirebaseNetworkException("No network")
        every { auth.signInWithCredential(any()) } returns Tasks.forException(ex)

        val result = gateway.signInWithGoogleIdToken("id-token-123")

        assertEquals(AuthResult.NetworkError, result)
    }

    @Test
    fun `signInWithGoogleIdToken unknown exception maps to Unknown`() = runTest {
        val ex = IllegalStateException("Unexpected failure")
        every { auth.signInWithCredential(any()) } returns Tasks.forException(ex)

        val result = gateway.signInWithGoogleIdToken("id-token-123")

        assertTrue("expected Unknown, got $result", result is AuthResult.Unknown)
        assertEquals(ex, (result as AuthResult.Unknown).cause)
    }

    // -------------------------------------------------------------------------
    // linkGoogleToCurrentUser
    // -------------------------------------------------------------------------

    @Test
    fun `linkGoogleToCurrentUser no signed-in user returns Unknown with IllegalStateException`() =
        runTest {
            // auth.currentUser is already null from setUp; no FirebaseUser set.
            val result = gateway.linkGoogleToCurrentUser("id-token-456")

            assertTrue("expected Unknown, got $result", result is AuthResult.Unknown)
            assertNotNull((result as AuthResult.Unknown).cause)
            assertTrue(result.cause is IllegalStateException)
        }

    @Test
    fun `linkGoogleToCurrentUser success returns AuthResult Success`() = runTest {
        val firebaseUser = mockk<com.google.firebase.auth.FirebaseUser>(relaxed = true)
        every { auth.currentUser } returns firebaseUser
        val task = Tasks.forResult(mockk<FirebaseAuthResult>(relaxed = true))
        every { firebaseUser.linkWithCredential(any()) } returns task

        val result = gateway.linkGoogleToCurrentUser("id-token-456")

        assertEquals(AuthResult.Success, result)
    }

    @Test
    fun `linkGoogleToCurrentUser network error maps to NetworkError`() = runTest {
        val firebaseUser = mockk<com.google.firebase.auth.FirebaseUser>(relaxed = true)
        every { auth.currentUser } returns firebaseUser
        val ex = FirebaseNetworkException("Offline")
        every { firebaseUser.linkWithCredential(any()) } returns Tasks.forException(ex)

        val result = gateway.linkGoogleToCurrentUser("id-token-456")

        assertEquals(AuthResult.NetworkError, result)
    }

    @Test
    fun `linkGoogleToCurrentUser InvalidCredentials exception maps to InvalidCredentials`() =
        runTest {
            val firebaseUser = mockk<com.google.firebase.auth.FirebaseUser>(relaxed = true)
            every { auth.currentUser } returns firebaseUser
            val ex = FirebaseAuthInvalidCredentialsException("ERROR_INVALID_ID_TOKEN", "Bad token")
            every { firebaseUser.linkWithCredential(any()) } returns Tasks.forException(ex)

            val result = gateway.linkGoogleToCurrentUser("bad-token")

            assertEquals(AuthResult.InvalidCredentials, result)
        }
}
