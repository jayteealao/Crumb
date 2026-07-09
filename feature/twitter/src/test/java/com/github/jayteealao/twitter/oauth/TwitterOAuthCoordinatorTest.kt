package com.github.jayteealao.twitter.oauth

import android.app.Activity
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TwitterOAuthCoordinatorTest {

    @Test
    fun handleDeepLink_complete_emitsSuccess() = runTest(UnconfinedTestDispatcher()) {
        val coordinator = TwitterOAuthCoordinator(mockk(relaxed = true))
        val results = mutableListOf<OAuthResult>()
        val collector = backgroundScope.launch {
            coordinator.results.collect { results += it }
        }
        yield()
        coordinator.handleDeepLink(Uri.parse("crumbs://graphitenerd.xyz/x-oauth-complete"))
        yield()
        assertTrue("expected at least one result", results.isNotEmpty())
        assertEquals(OAuthResult.Success, results.first())
        collector.cancel()
    }

    @Test
    fun handleDeepLink_error_emitsFailureWithReason() = runTest(UnconfinedTestDispatcher()) {
        val coordinator = TwitterOAuthCoordinator(mockk(relaxed = true))
        val results = mutableListOf<OAuthResult>()
        val collector = backgroundScope.launch {
            coordinator.results.collect { results += it }
        }
        yield()
        coordinator.handleDeepLink(
            Uri.parse("crumbs://graphitenerd.xyz/x-oauth-error?reason=invalid_grant"),
        )
        yield()
        assertEquals(1, results.size)
        assertEquals(OAuthResult.Failure("invalid_grant"), results.first())
        collector.cancel()
    }

    @Test
    fun handleDeepLink_unknownPath_emitsNothing() = runTest(UnconfinedTestDispatcher()) {
        val coordinator = TwitterOAuthCoordinator(mockk(relaxed = true))
        val results = mutableListOf<OAuthResult>()
        val collector = backgroundScope.launch {
            coordinator.results.collect { results += it }
        }
        yield()
        coordinator.handleDeepLink(Uri.parse("crumbs://graphitenerd.xyz/?code=abc"))
        yield()
        assertTrue("expected no emissions, got $results", results.isEmpty())
        collector.cancel()
    }

    @Test
    fun mintOAuthState_unauthenticated_emitsUnauthenticatedFailure() =
        runTest(UnconfinedTestDispatcher()) {
            // FirebaseFunctionsException's primary constructor is internal in Kotlin but
            // public in Java bytecode — use reflection to construct it cross-module in tests.
            val unauthException = FirebaseFunctionsException::class.java
                .getDeclaredConstructor(
                    String::class.java,
                    FirebaseFunctionsException.Code::class.java,
                    Any::class.java,
                )
                .apply { isAccessible = true }
                .newInstance(
                    "Sign-in required",
                    FirebaseFunctionsException.Code.UNAUTHENTICATED,
                    null,
                )

            // warmUp succeeds (relaxed); mintOAuthState throws UNAUTHENTICATED.
            val warmUpCallable = mockk<HttpsCallableReference>(relaxed = true)
            val mintCallable = mockk<HttpsCallableReference>()
            val functions = mockk<FirebaseFunctions>()
            every { functions.getHttpsCallable("warmUp") } returns warmUpCallable
            every { functions.getHttpsCallable("mintOAuthState") } returns mintCallable
            every { warmUpCallable.call() } returns Tasks.forResult(
                mockk<HttpsCallableResult>(relaxed = true),
            )
            every { mintCallable.call(any<Map<String, Any>>()) } returns
                Tasks.forException(unauthException)

            val coordinator = TwitterOAuthCoordinator(functions)
            val results = mutableListOf<OAuthResult>()
            val collector = backgroundScope.launch {
                coordinator.results.collect { results += it }
            }
            yield()

            coordinator.launchAuthorize(mockk<Activity>(relaxed = true))
            yield()

            assertEquals(1, results.size)
            assertEquals(OAuthResult.Failure(OAuthResult.Failure.REASON_UNAUTHENTICATED), results.first())
            collector.cancel()
        }
}
