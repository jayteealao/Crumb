package com.github.jayteealao.crumbs.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Validates the [SyncErrorBus] contract that drives the HomeRoute auth-error banner.
 *
 * Two contracts are pinned:
 *  1) emit() delivers the event to an active collector (basic bus behaviour).
 *  2) replay = 1 preserves the most-recent emission for a *late* subscriber.
 *     This is the behaviour that fixes the cold-start auth-failure regression: if
 *     Repository.init() emits a 401 before HomeRoute composes its collector, the
 *     event must still surface to the late subscriber instead of being silently
 *     dropped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncErrorBusTest {

    @Test
    fun emit_delivers_event_to_active_collector() = runBlocking {
        val bus = SyncErrorBus()
        val scope = CoroutineScope(Dispatchers.Default)
        val deferredEvent = scope.async { bus.events.first() }
        // Give the collector a chance to subscribe before emission.
        yield()
        repeat(20) { yield() }

        val accepted = bus.emit(SyncErrorEvent.TwitterAuth401())
        assertTrue("emit() must return true when buffer/replay can accept the event", accepted)

        val received = deferredEvent.await()
        assertTrue(
            "Active collector should receive TwitterAuth401 but got ${received::class.simpleName}",
            received is SyncErrorEvent.TwitterAuth401,
        )
    }

    @Test
    fun emit_before_subscriber_is_replayed_to_late_collector() = runBlocking {
        // Reproduces the cold-start auth-failure path: Repository.init() runs and
        // emits an error before HomeRoute's LaunchedEffect attaches its collector.
        // replay = 1 must preserve that event so the banner appears when the UI
        // finally subscribes.
        val bus = SyncErrorBus()

        bus.emit(SyncErrorEvent.RedditAuth401())

        val received = bus.events.first()
        assertTrue(
            "Late subscriber must receive the replayed event; got ${received::class.simpleName}",
            received is SyncErrorEvent.RedditAuth401,
        )
    }

    @Test
    fun multiple_emits_keep_latest_for_late_collector() = runBlocking {
        // With replay = 1 + DROP_OLDEST, a late subscriber sees only the latest
        // event. This is the intended UX — the most-recent auth failure is what
        // the user needs to act on; earlier ones are obsolete.
        val bus = SyncErrorBus()

        bus.emit(SyncErrorEvent.TwitterAuth401())
        bus.emit(SyncErrorEvent.RedditAuth401())

        val received = bus.events.first()
        assertTrue(
            "Latest emission should win for late subscriber; got ${received::class.simpleName}",
            received is SyncErrorEvent.RedditAuth401,
        )
        assertEquals(
            "Latest event's source should be Reddit",
            com.github.jayteealao.crumbs.models.BookmarkSource.Reddit,
            received.source,
        )
    }
}
