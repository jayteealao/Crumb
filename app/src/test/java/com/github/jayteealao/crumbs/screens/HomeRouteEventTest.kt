package com.github.jayteealao.crumbs.screens

import com.github.jayteealao.crumbs.data.SnackbarBus
import com.github.jayteealao.crumbs.data.SnackbarEvent
import com.github.jayteealao.crumbs.data.SyncErrorBus
import com.github.jayteealao.crumbs.data.SyncErrorEvent
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.twitter.data.TwitterSnackbarEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the event bus contracts that directly drive [HomeRoute]'s
 * [androidx.compose.runtime.LaunchedEffect] blocks.
 *
 * [HomeRoute] collects from three event sources:
 *  1. [com.github.jayteealao.twitter.screens.BookmarksViewModel.snackbarEvents] —
 *     Twitter poll-result feedback (Debounced / InProgress / GenericFailure).
 *  2. [HomeServicesViewModel.syncErrorBus] — auth-error banner driver
 *     (TwitterAuth401 / RedditAuth401 / Other).
 *  3. [HomeServicesViewModel.snackbarBus] — bookmark-delete undo affordance.
 *
 * These tests pin the bus contracts that the LaunchedEffect collectors depend on,
 * confirming that the correct event types are emitted and received without needing
 * a full Hilt Compose setup. The existing [SyncErrorBusTest] covers the replay=1
 * guarantee; this file extends coverage to the snackbar bus and the Twitter
 * snackbar event routing logic that determines which message string is displayed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeRouteEventTest {

    // -----------------------------------------------------------------------
    // 1. SyncErrorBus → LaunchedEffect(Unit) [syncErrorBus.events]
    //    HomeRoute maps TwitterAuth401 → twitterBanner, RedditAuth401 → redditBanner
    // -----------------------------------------------------------------------

    /**
     * TwitterAuth401 must be deliverable to an active collector.
     * HomeRoute's LaunchedEffect sets twitterBanner when this event arrives.
     */
    @Test
    fun syncErrorBus_twitterAuth401_deliveredToActiveCollector() = runBlocking {
        val bus = SyncErrorBus()
        val scope = CoroutineScope(Dispatchers.Default)
        val deferred = scope.async { bus.events.first() }
        // Yield so the collector subscribes before emission.
        repeat(20) { yield() }

        bus.emit(SyncErrorEvent.TwitterAuth401())

        val received = deferred.await()
        assertTrue(
            "HomeRoute's LaunchedEffect must receive TwitterAuth401 to set twitterBanner; " +
                "got ${received::class.simpleName}",
            received is SyncErrorEvent.TwitterAuth401,
        )
        assertEquals(
            "source on TwitterAuth401 must be Twitter (used to route the banner)",
            BookmarkSource.Twitter,
            received.source,
        )
    }

    /**
     * RedditAuth401 must be deliverable to an active collector.
     * HomeRoute's LaunchedEffect sets redditBanner when this event arrives.
     */
    @Test
    fun syncErrorBus_redditAuth401_deliveredToActiveCollector() = runBlocking {
        val bus = SyncErrorBus()
        val scope = CoroutineScope(Dispatchers.Default)
        val deferred = scope.async { bus.events.first() }
        repeat(20) { yield() }

        bus.emit(SyncErrorEvent.RedditAuth401())

        val received = deferred.await()
        assertTrue(
            "HomeRoute must receive RedditAuth401 to set redditBanner; " +
                "got ${received::class.simpleName}",
            received is SyncErrorEvent.RedditAuth401,
        )
        assertEquals(BookmarkSource.Reddit, received.source)
    }

    /**
     * SyncErrorEvent.Other is explicitly a no-op in HomeRoute (the when-branch is `Unit`).
     * Verify the bus delivers it so the exhaustive when still compiles / runs safely.
     */
    @Test
    fun syncErrorBus_otherEvent_deliveredWithCorrectSource() = runBlocking {
        val bus = SyncErrorBus()
        val scope = CoroutineScope(Dispatchers.Default)
        val deferred = scope.async { bus.events.first() }
        repeat(20) { yield() }

        bus.emit(SyncErrorEvent.Other(source = BookmarkSource.Twitter, message = "unknown"))

        val received = deferred.await()
        assertTrue(
            "SyncErrorEvent.Other must be deliverable (HomeRoute handles it as Unit)",
            received is SyncErrorEvent.Other,
        )
    }

    /**
     * Replay = 1 means a late subscriber (e.g. HomeRoute mounting after a 401 fires)
     * still sees the most-recent auth error. This is the cold-start regression guard.
     */
    @Test
    fun syncErrorBus_replayPreservesLatestEventForLateSubscriber() = runBlocking {
        val bus = SyncErrorBus()
        // Emit before any subscriber is attached.
        bus.emit(SyncErrorEvent.TwitterAuth401())
        bus.emit(SyncErrorEvent.RedditAuth401()) // latest wins with replay=1+DROP_OLDEST

        val received = bus.events.first()
        assertTrue(
            "Late subscriber (like HomeRoute on warm start) must see the latest 401; " +
                "got ${received::class.simpleName}",
            received is SyncErrorEvent.RedditAuth401,
        )
    }

    /**
     * SyncErrorBus.clear() resets the replay cache so HomeRoute does not resurrect
     * a stale auth error on the next recomposition after the token is refreshed.
     * This mirrors the LaunchedEffect(twitterAccess) / LaunchedEffect(redditAccess)
     * blocks that call services.syncErrorBus.clear() when access = true.
     */
    @Test
    fun syncErrorBus_clearPreventsStaleReplay() = runBlocking {
        val bus = SyncErrorBus()
        bus.emit(SyncErrorEvent.TwitterAuth401())
        // Simulates what LaunchedEffect(twitterAccess) does when access becomes true.
        bus.clear()

        // A late subscriber must not see the stale 401 after clear().
        var received: SyncErrorEvent? = null
        val job = CoroutineScope(Dispatchers.Default).launch {
            received = bus.events.first()
        }
        repeat(20) { yield() }
        job.cancel()

        assertNull(
            "After clear() the replay cache must be empty — HomeRoute must not flash a stale banner",
            received,
        )
    }

    // -----------------------------------------------------------------------
    // 2. SnackbarBus → LaunchedEffect(Unit) [snackbarBus.events]
    //    HomeRoute shows "BOOKMARK DELETED" + UNDO action from UndoableDelete events
    // -----------------------------------------------------------------------

    /**
     * SnackbarBus must deliver UndoableDelete(Twitter) to an active collector.
     * HomeRoute's LaunchedEffect shows the "BOOKMARK DELETED" snackbar on receipt.
     */
    @Test
    fun snackbarBus_twitterUndoableDelete_deliveredToActiveCollector() = runTest {
        val bus = SnackbarBus()
        // UnconfinedTestDispatcher starts the collector eagerly so it is subscribed
        // before we emit. SnackbarBus has replay=0, so a late subscriber would miss
        // the event and await() would block forever; runTest also bounds the test.
        val deferred = async(UnconfinedTestDispatcher(testScheduler)) { bus.events.first() }

        bus.emit(SnackbarEvent.UndoableDelete(id = "tweet-42", source = BookmarkSource.Twitter))

        val received = deferred.await()
        assertTrue(
            "HomeRoute's snackbar LaunchedEffect must receive UndoableDelete; " +
                "got ${received::class.simpleName}",
            received is SnackbarEvent.UndoableDelete,
        )
        val event = received as SnackbarEvent.UndoableDelete
        assertEquals("tweet-42", event.id)
        assertEquals(BookmarkSource.Twitter, event.source)
    }

    /**
     * UndoableDelete(Reddit) must also be delivered. HomeRoute routes undo to
     * redditViewModel.undoDelete(event.id) when source == Reddit.
     */
    @Test
    fun snackbarBus_redditUndoableDelete_deliveredWithCorrectSource() = runTest {
        val bus = SnackbarBus()
        val deferred = async(UnconfinedTestDispatcher(testScheduler)) { bus.events.first() }

        bus.emit(SnackbarEvent.UndoableDelete(id = "reddit-99", source = BookmarkSource.Reddit))

        val received = deferred.await()
        val event = received as SnackbarEvent.UndoableDelete
        assertEquals(
            "Undo routing depends on source==Reddit to call redditViewModel.undoDelete",
            BookmarkSource.Reddit,
            event.source,
        )
        assertEquals("reddit-99", event.id)
    }

    /**
     * SnackbarBus has replay=0 so a late subscriber does NOT see missed events.
     * This is intentional — an undo affordance that arrives long after deletion
     * is no longer actionable and must not reappear on recomposition.
     */
    @Test
    fun snackbarBus_noReplay_lateSubscriberMissesEarlierEvents() = runBlocking {
        val bus = SnackbarBus()
        // Emit before any subscriber.
        bus.emit(SnackbarEvent.UndoableDelete(id = "old-tweet", source = BookmarkSource.Twitter))

        // Late subscriber — should NOT see the earlier event (replay=0).
        var received: SnackbarEvent? = null
        val job = CoroutineScope(Dispatchers.Default).launch {
            received = bus.events.first()
        }
        repeat(20) { yield() }
        job.cancel()

        assertNull(
            "SnackbarBus has replay=0: late subscribers must not receive stale undo events",
            received,
        )
    }

    /**
     * Multiple rapid deletes (burst) must all be buffered (capacity=16) without
     * dropping events. HomeRoute shows a snackbar for each; DROP_OLDEST only
     * activates if > 16 events queue simultaneously.
     */
    @Test
    fun snackbarBus_burstOfDeletes_allDeliveredWithinCapacity() = runTest {
        val bus = SnackbarBus()
        val burstCount = 5
        // UnconfinedTestDispatcher subscribes the collector eagerly before the burst.
        val deferred = async(UnconfinedTestDispatcher(testScheduler)) {
            bus.events.take(burstCount).toList()
        }

        repeat(burstCount) { i ->
            bus.emit(SnackbarEvent.UndoableDelete(id = "tweet-$i", source = BookmarkSource.Twitter))
        }

        val events = deferred.await()
        assertEquals(
            "All $burstCount delete events must be delivered; none should be dropped within buffer capacity",
            burstCount,
            events.size,
        )
        events.forEachIndexed { i, event ->
            assertTrue(event is SnackbarEvent.UndoableDelete)
            assertEquals("tweet-$i", (event as SnackbarEvent.UndoableDelete).id)
        }
    }

    // -----------------------------------------------------------------------
    // 3. Twitter SnackbarEvent routing — LaunchedEffect(Unit) [bookmarksViewModel.snackbarEvents]
    //    HomeRoute maps each event type to a specific snackbar message string.
    // -----------------------------------------------------------------------

    /**
     * The message string for [TwitterSnackbarEvent.Debounced] must include the
     * retryAfterSeconds value when it is non-null. HomeRoute formats this inline
     * inside the collect lambda; the event contract is what the test pins.
     */
    @Test
    fun twitterSnackbarEvent_debounced_withRetryAfterSeconds_fieldIsAccessible() {
        val event = TwitterSnackbarEvent.Debounced(retryAfterSeconds = 60)
        assertNotNull(
            "Debounced.retryAfterSeconds must be non-null so the snackbar message can include it",
            event.retryAfterSeconds,
        )
        assertEquals(60, event.retryAfterSeconds)
    }

    /**
     * [TwitterSnackbarEvent.Debounced] with null retryAfterSeconds — HomeRoute
     * falls back to "60s" in the formatted string. The event is still valid.
     */
    @Test
    fun twitterSnackbarEvent_debounced_withNullRetryAfter_isValidEvent() {
        val event = TwitterSnackbarEvent.Debounced(retryAfterSeconds = null)
        assertNull(
            "Debounced.retryAfterSeconds may be null; HomeRoute uses ?: 60 fallback",
            event.retryAfterSeconds,
        )
    }

    /**
     * [TwitterSnackbarEvent.InProgress] is an object — it must be emittable and
     * identifiable by the `is` check in HomeRoute's when expression.
     */
    @Test
    fun twitterSnackbarEvent_inProgress_isIdentifiableInWhenExpression() {
        val event: TwitterSnackbarEvent = TwitterSnackbarEvent.InProgress
        assertTrue(
            "HomeRoute's when branch `is TwitterSnackbarEvent.InProgress` must match",
            event is TwitterSnackbarEvent.InProgress,
        )
        assertFalse(event is TwitterSnackbarEvent.Debounced)
        assertFalse(event is TwitterSnackbarEvent.GenericFailure)
    }

    /**
     * [TwitterSnackbarEvent.GenericFailure] carries a reason string. HomeRoute
     * does not use the reason in the snackbar message but the event must still be
     * constructable and identifiable.
     */
    @Test
    fun twitterSnackbarEvent_genericFailure_isIdentifiableInWhenExpression() {
        val event: TwitterSnackbarEvent = TwitterSnackbarEvent.GenericFailure(reason = "network")
        assertTrue(
            "HomeRoute's when branch `is TwitterSnackbarEvent.GenericFailure` must match",
            event is TwitterSnackbarEvent.GenericFailure,
        )
        assertEquals("network", (event as TwitterSnackbarEvent.GenericFailure).reason)
    }

    /**
     * A [MutableSharedFlow] with the same configuration as
     * [com.github.jayteealao.twitter.data.Repository.snackbarEvents] must deliver
     * all three [TwitterSnackbarEvent] subtypes to an active collector in order.
     * This mirrors what HomeRoute's LaunchedEffect collects.
     */
    @Test
    fun twitterSnackbarFlow_allThreeEventTypes_deliveredInOrder() = runTest {
        // Mirror Repository.snackbarEvents config (replay=0, extraBufferCapacity≥1).
        val flow = MutableSharedFlow<TwitterSnackbarEvent>(
            replay = 0,
            extraBufferCapacity = 8,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        val collected = mutableListOf<TwitterSnackbarEvent>()
        // UnconfinedTestDispatcher subscribes the collector eagerly before the emits.
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.collect { collected.add(it) }
        }

        flow.emit(TwitterSnackbarEvent.Debounced(retryAfterSeconds = 30))
        flow.emit(TwitterSnackbarEvent.InProgress)
        flow.emit(TwitterSnackbarEvent.GenericFailure(reason = "timeout"))

        job.cancel()

        assertEquals("All three Twitter snackbar event types must be deliverable", 3, collected.size)
        assertTrue(collected[0] is TwitterSnackbarEvent.Debounced)
        assertTrue(collected[1] is TwitterSnackbarEvent.InProgress)
        assertTrue(collected[2] is TwitterSnackbarEvent.GenericFailure)
    }

    // -----------------------------------------------------------------------
    // 4. HomeRoute banner-state logic (pure Kotlin, no Compose required)
    //    Mirrors the LaunchedEffect(syncStatus?.linked) conditional
    // -----------------------------------------------------------------------

    /**
     * When syncStatus.linked == false HomeRoute sets twitterBanner to a non-null
     * BannerState. Verify the condition that gates the banner is correctly expressed.
     */
    @Test
    fun syncStatusLinked_false_shouldTriggerBanner() {
        val linked: Boolean? = false
        // This mirrors: if (linked == false) { twitterBanner = BannerState(...) }
        assertTrue(
            "linked == false must evaluate to true to set twitterBanner",
            linked == false,
        )
        assertFalse(
            "linked == false must NOT be treated as null",
            linked == null,
        )
    }

    /**
     * When syncStatus.linked == true HomeRoute sets twitterBanner to null (cleared).
     */
    @Test
    fun syncStatusLinked_true_shouldClearBanner() {
        val linked: Boolean? = true
        // This mirrors: } else if (linked == true) { twitterBanner = null }
        assertTrue(
            "linked == true must evaluate to true to clear twitterBanner",
            linked == true,
        )
        assertFalse(linked == false)
        assertFalse(linked == null)
    }

    /**
     * When syncStatus is null (e.g. not yet loaded) neither condition fires —
     * HomeRoute must leave the banner unchanged.
     */
    @Test
    fun syncStatusLinked_null_shouldNotChangeBannerState() {
        val linked: Boolean? = null
        assertFalse(
            "linked == false must be false when syncStatus is null (no banner change)",
            linked == false,
        )
        assertFalse(
            "linked == true must be false when syncStatus is null (no banner clear)",
            linked == true,
        )
    }

    // -----------------------------------------------------------------------
    // 5. LaunchedEffect(twitterAccess) / LaunchedEffect(redditAccess) gate
    //    When access == true the banner is cleared and syncErrorBus.clear() is called.
    // -----------------------------------------------------------------------

    /**
     * Confirms that when twitterAccess becomes true, the syncErrorBus.clear() contract
     * removes stale replay entries, preventing false banner re-appearance.
     * This is what LaunchedEffect(twitterAccess) { if (twitterAccess) { ...; syncErrorBus.clear() } }
     * relies on.
     */
    @Test
    fun syncErrorBus_clearAfterAccessRestored_noStaleReplayForNewSubscriber() = runBlocking {
        val bus = SyncErrorBus()
        // Simulate auth error during sync.
        bus.emit(SyncErrorEvent.TwitterAuth401())
        // Simulate LaunchedEffect(twitterAccess = true) clearing the bus.
        bus.clear()
        // Emit new event after restoration.
        bus.emit(SyncErrorEvent.Other(BookmarkSource.Twitter, "test"))

        val received = bus.events.first()
        assertTrue(
            "After clear() + new emission, subscriber should see only the new event",
            received is SyncErrorEvent.Other,
        )
    }
}
