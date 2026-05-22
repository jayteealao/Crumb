package com.github.jayteealao.twitter.oauth

import android.net.Uri
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
}
