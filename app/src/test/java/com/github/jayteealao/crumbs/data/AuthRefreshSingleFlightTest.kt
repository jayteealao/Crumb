package com.github.jayteealao.crumbs.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Validates the [withAuthRefreshSingleFlight] contract that Reddit's auth
 * refresh wrapper still delegates to (Twitter's wrapper was removed by the
 * server-side cutover).
 *
 * Tests pin the four invariants of the contract:
 *  1. doRefresh return value is faithfully propagated
 *  2. Exceptions are caught and converted to `false` (mutex still released)
 *  3. Two concurrent callers serialize on the mutex (single-flight)
 *  4. Mutex is released even on exception so the next call can acquire it
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthRefreshSingleFlightTest {

    @Test
    fun returns_true_when_do_refresh_returns_true() = runBlocking {
        val ok = withAuthRefreshSingleFlight(Mutex(), tag = "test") { true }
        assertTrue("Helper must propagate doRefresh's true", ok)
    }

    @Test
    fun returns_false_when_do_refresh_returns_false() = runBlocking {
        val ok = withAuthRefreshSingleFlight(Mutex(), tag = "test") { false }
        assertFalse("Helper must propagate doRefresh's false", ok)
    }

    @Test
    fun returns_false_when_do_refresh_throws() = runBlocking {
        val ok = withAuthRefreshSingleFlight(Mutex(), tag = "test") {
            throw IllegalStateException("network died")
        }
        assertFalse(
            "Exception inside doRefresh must surface as false, not escape",
            ok,
        )
    }

    @Test
    fun mutex_released_after_exception_so_next_call_runs() = runBlocking {
        val mutex = Mutex()

        val first = withAuthRefreshSingleFlight(mutex, tag = "test") {
            throw IllegalStateException("boom")
        }
        assertFalse(first)

        // If the previous call had left the mutex locked, this would deadlock.
        // runBlocking gives us a finite window — if we get here with a result
        // the mutex was correctly released by the helper's try/finally.
        val second = withAuthRefreshSingleFlight(mutex, tag = "test") { true }
        assertTrue("Helper must release the mutex on exception", second)
    }

    @Test
    fun two_concurrent_callers_serialize_through_the_mutex() = runBlocking {
        val mutex = Mutex()
        val concurrentInside = AtomicInteger(0)
        val maxConcurrentInside = AtomicInteger(0)
        val invocations = AtomicInteger(0)
        val scope = CoroutineScope(Dispatchers.Default)

        // Launch 10 callers in parallel. Each is single-flighted by `mutex`,
        // so the maximum observed concurrency inside doRefresh must be 1.
        // This is the regression test for the R2-REL-01/CONC-1 fix: the prior
        // `tryLock` + return true skipped the mutex entirely, which let
        // concurrent callers run in parallel and observe stale state.
        val results = (1..10).map {
            scope.async {
                withAuthRefreshSingleFlight(mutex, tag = "test-$it") {
                    val now = concurrentInside.incrementAndGet()
                    maxConcurrentInside.updateAndGet { it.coerceAtLeast(now) }
                    // Yield so the runtime can schedule any sibling that
                    // bypasses the mutex; if single-flight is broken, the
                    // max will tick above 1.
                    repeat(5) { yield() }
                    invocations.incrementAndGet()
                    concurrentInside.decrementAndGet()
                    true
                }
            }
        }.awaitAll()

        assertEquals("All 10 callers should observe success", List(10) { true }, results)
        assertEquals("All 10 doRefresh bodies should have executed", 10, invocations.get())
        assertEquals(
            "Single-flight: at most one caller inside the lambda at any time",
            1,
            maxConcurrentInside.get(),
        )
    }
}
