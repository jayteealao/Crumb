package com.github.jayteealao.twitter.screens

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.github.jayteealao.crumbs.data.FilterState
import com.github.jayteealao.crumbs.data.TypeFilter
import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.models.ContentType
import com.github.jayteealao.crumbs.models.VideoVariant
import com.github.jayteealao.twitter.data.Repository
import com.github.jayteealao.twitter.data.TwitterSnackbarEvent
import com.github.jayteealao.twitter.data.SyncStatusRepository
import com.github.jayteealao.twitter.data.dto.SyncStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [BookmarksViewModel].
 *
 * Uses [StandardTestDispatcher] for deterministic coroutine scheduling.
 * Dependencies ([Repository] and [SyncStatusRepository]) are [mockk] fakes.
 *
 * Notes on coverage:
 *  - Filter state ([_filter]) is a plain [MutableStateFlow] so its value is
 *    readable immediately after mutation — no WhileSubscribed activation needed.
 *  - [pagingFlow] delegates to [Repository.pagingTweetData] which is mocked;
 *    correctness of the Flow itself is tested in the paging layer — we only
 *    verify the ViewModel reacts to filter changes.
 *  - All `viewModelScope.launch` coroutines run on the test dispatcher and are
 *    drained with [advanceUntilIdle].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookmarksViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var repository: Repository
    private lateinit var syncStatusRepository: SyncStatusRepository
    private lateinit var vm: BookmarksViewModel

    // Backing state flows that the mocked repositories expose
    private val isRefreshingFlow = MutableStateFlow(false)
    private val syncStatusFlow = MutableStateFlow<SyncStatus?>(null)
    private val snackbarEventsFlow = MutableSharedFlow<TwitterSnackbarEvent>(replay = 0, extraBufferCapacity = 4)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        repository = mockk(relaxed = true)
        syncStatusRepository = mockk(relaxed = true)

        // Wire mock flows so the ViewModel constructor properties delegate correctly
        every { repository.isRefreshing } returns isRefreshingFlow
        every { repository.snackbarEvents } returns snackbarEventsFlow
        every { syncStatusRepository.flow } returns syncStatusFlow

        // Stub pagingTweetData so pagingFlow can be built without a real DB
        every { repository.pagingTweetData(any()) } returns MutableSharedFlow(replay = 0)

        // syncStatusRepository.refresh() is a suspend fun — relaxed mock returns Unit/null
        coEvery { syncStatusRepository.refresh(any()) } returns null

        vm = BookmarksViewModel(
            context = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(),
            repository = repository,
            syncStatusRepository = syncStatusRepository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // 1. Initial state
    // -------------------------------------------------------------------------

    @Test
    fun initialFilter_isDefault() = runTest(dispatcher) {
        advanceUntilIdle()
        val filter = vm.filter.value
        assertEquals(FilterState(), filter)
        assertEquals(TypeFilter.ALL, filter.type)
        assertTrue(filter.selectedTags.isEmpty())
    }

    @Test
    fun initialIsRefreshing_isFalse() = runTest(dispatcher) {
        advanceUntilIdle()
        assertFalse(vm.isRefreshing.value)
    }

    @Test
    fun initialSyncStatus_isNull() = runTest(dispatcher) {
        advanceUntilIdle()
        assertNull(vm.syncStatus.value)
    }

    @Test
    fun initialTagsForTweet_isEmpty() = runTest(dispatcher) {
        advanceUntilIdle()
        assertTrue(vm.tagsForTweet.value.isEmpty())
    }

    @Test
    fun initialAllTags_isEmptyUntilLoaded() = runTest(dispatcher) {
        // Before coroutines drain, allTags is the default empty list
        assertTrue(vm.allTags.value.isEmpty())
    }

    // -------------------------------------------------------------------------
    // 1b. refetchMediaIfMissing — single-tweet media re-fetch (legacy media-less
    //     corpus). The relaxed Repository mock returns false (no media) by default.
    // -------------------------------------------------------------------------

    @Test
    fun refetchMediaIfMissing_firstCall_invokesRepositoryOnce() = runTest(dispatcher) {
        coEvery { repository.refetchTweetMedia("t1") } returns true
        vm.refetchMediaIfMissing("t1")
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.refetchTweetMedia("t1") }
    }

    @Test
    fun refetchMediaIfMissing_sameIdTwice_invokesRepositoryOnlyOnce() = runTest(dispatcher) {
        // Per-session attempted-set dedup: a media-less tweet already attempted this
        // session must not re-hit Firestore on every scroll-into-view.
        vm.refetchMediaIfMissing("t1")
        advanceUntilIdle()
        vm.refetchMediaIfMissing("t1")
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.refetchTweetMedia("t1") }
    }

    @Test
    fun refetchMediaIfMissing_repositoryThrows_isSwallowedAndAttemptStillRecorded() = runTest(dispatcher) {
        coEvery { repository.refetchTweetMedia("t1") } throws RuntimeException("network down")
        // runCatching in the ViewModel swallows the failure — the card settles to
        // text-only rather than crashing.
        vm.refetchMediaIfMissing("t1")
        advanceUntilIdle()
        // The attempt is recorded before the launch, so a same-session retry does not
        // re-hit the repository even though the first attempt failed.
        vm.refetchMediaIfMissing("t1")
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.refetchTweetMedia("t1") }
    }

    @Test
    fun refetchMediaIfMissing_distinctIds_eachInvokedOnce() = runTest(dispatcher) {
        vm.refetchMediaIfMissing("t1")
        vm.refetchMediaIfMissing("t2")
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.refetchTweetMedia("t1") }
        coVerify(exactly = 1) { repository.refetchTweetMedia("t2") }
    }

    // -------------------------------------------------------------------------
    // 1c. refetchLinksIfMissing — single-tweet link re-fetch (tweet with no
    //     resolved url entity). Mirrors the refetchMediaIfMissing test shape.
    // -------------------------------------------------------------------------

    @Test
    fun refetchLinksIfMissing_firstCall_invokesRepositoryOnce() = runTest(dispatcher) {
        coEvery { repository.refetchTweetLinks("t1") } returns true
        vm.refetchLinksIfMissing("t1")
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.refetchTweetLinks("t1") }
    }

    @Test
    fun refetchLinksIfMissing_sameIdTwice_invokesRepositoryOnlyOnce() = runTest(dispatcher) {
        // Per-session linkRefetchAttempts dedup: calling twice for the same id must
        // not re-hit Firestore on every scroll-into-view.
        vm.refetchLinksIfMissing("t1")
        advanceUntilIdle()
        vm.refetchLinksIfMissing("t1")
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.refetchTweetLinks("t1") }
    }

    @Test
    fun refetchLinksIfMissing_repositoryThrows_isSwallowedAndAttemptStillRecorded() = runTest(dispatcher) {
        coEvery { repository.refetchTweetLinks("t1") } throws RuntimeException("network down")
        // runCatching in the ViewModel swallows the failure — the card settles to
        // text-only rather than crashing.
        vm.refetchLinksIfMissing("t1")
        advanceUntilIdle()
        // The attempt is recorded before the launch, so a same-session retry does not
        // re-hit the repository even though the first attempt failed.
        vm.refetchLinksIfMissing("t1")
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.refetchTweetLinks("t1") }
    }

    @Test
    fun refetchLinksIfMissing_distinctIds_eachInvokedOnce() = runTest(dispatcher) {
        vm.refetchLinksIfMissing("t1")
        vm.refetchLinksIfMissing("t2")
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.refetchTweetLinks("t1") }
        coVerify(exactly = 1) { repository.refetchTweetLinks("t2") }
    }

    // -------------------------------------------------------------------------
    // 1d. refetchQuotesIfMissing — single-tweet quoted-body re-fetch (tweet
    //     whose quoted body hasn't landed locally). Mirrors the same shape.
    // -------------------------------------------------------------------------

    @Test
    fun refetchQuotesIfMissing_firstCall_invokesRepositoryOnce() = runTest(dispatcher) {
        coEvery { repository.refetchTweetQuotes("t1") } returns true
        vm.refetchQuotesIfMissing("t1")
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.refetchTweetQuotes("t1") }
    }

    @Test
    fun refetchQuotesIfMissing_sameIdTwice_invokesRepositoryOnlyOnce() = runTest(dispatcher) {
        // Per-session quoteRefetchAttempts dedup: calling twice for the same id must
        // not re-hit Firestore on every scroll-into-view.
        vm.refetchQuotesIfMissing("t1")
        advanceUntilIdle()
        vm.refetchQuotesIfMissing("t1")
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.refetchTweetQuotes("t1") }
    }

    @Test
    fun refetchQuotesIfMissing_repositoryThrows_isSwallowedAndAttemptStillRecorded() = runTest(dispatcher) {
        coEvery { repository.refetchTweetQuotes("t1") } throws RuntimeException("network down")
        // runCatching in the ViewModel swallows the failure — the card settles to
        // "unavailable" rather than crashing.
        vm.refetchQuotesIfMissing("t1")
        advanceUntilIdle()
        // The attempt is recorded before the launch, so a same-session retry does not
        // re-hit the repository even though the first attempt failed.
        vm.refetchQuotesIfMissing("t1")
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.refetchTweetQuotes("t1") }
    }

    @Test
    fun refetchQuotesIfMissing_distinctIds_eachInvokedOnce() = runTest(dispatcher) {
        vm.refetchQuotesIfMissing("t1")
        vm.refetchQuotesIfMissing("t2")
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.refetchTweetQuotes("t1") }
        coVerify(exactly = 1) { repository.refetchTweetQuotes("t2") }
    }

    // -------------------------------------------------------------------------
    // 2. syncStatusRepository.refresh() called during init
    // -------------------------------------------------------------------------

    @Test
    fun init_callsSyncStatusRefresh() = runTest(dispatcher) {
        advanceUntilIdle()
        // Called once during init block
        coVerify(atLeast = 1) { syncStatusRepository.refresh() }
    }

    // -------------------------------------------------------------------------
    // 3. isRefreshing proxies from repository
    // -------------------------------------------------------------------------

    @Test
    fun isRefreshing_reflectsRepositoryState() = runTest(dispatcher) {
        advanceUntilIdle()
        assertFalse(vm.isRefreshing.value)

        isRefreshingFlow.value = true
        advanceUntilIdle()
        assertTrue(vm.isRefreshing.value)

        isRefreshingFlow.value = false
        advanceUntilIdle()
        assertFalse(vm.isRefreshing.value)
    }

    // -------------------------------------------------------------------------
    // 4. syncStatus proxies from syncStatusRepository
    // -------------------------------------------------------------------------

    @Test
    fun syncStatus_reflectsRepositoryFlow() = runTest(dispatcher) {
        advanceUntilIdle()
        assertNull(vm.syncStatus.value)

        val status = SyncStatus(linked = true, xUserId = "x123")
        syncStatusFlow.value = status
        advanceUntilIdle()
        assertEquals(status, vm.syncStatus.value)
    }

    // -------------------------------------------------------------------------
    // 5. onTypeChipToggled updates filter
    // -------------------------------------------------------------------------

    @Test
    fun onTypeChipToggled_updatesFilterType() = runTest(dispatcher) {
        advanceUntilIdle()
        assertEquals(TypeFilter.ALL, vm.filter.value.type)

        vm.onTypeChipToggled("VIDEO")
        assertEquals(TypeFilter.VIDEO, vm.filter.value.type)
    }

    @Test
    fun onTypeChipToggled_unknownId_defaultsToAll() = runTest(dispatcher) {
        vm.onTypeChipToggled("VIDEO")
        assertEquals(TypeFilter.VIDEO, vm.filter.value.type)

        vm.onTypeChipToggled("UNKNOWN_TYPE")
        // valueOf throws for unknown names — runCatching defaults to ALL
        assertEquals(TypeFilter.ALL, vm.filter.value.type)
    }

    // -------------------------------------------------------------------------
    // 6. onTagToggled adds and removes tags
    // -------------------------------------------------------------------------

    @Test
    fun onTagToggled_addsTagWhenAbsent() = runTest(dispatcher) {
        vm.onTagToggled("kotlin")
        assertTrue(vm.filter.value.selectedTags.contains("kotlin"))
    }

    @Test
    fun onTagToggled_removesTagWhenPresent() = runTest(dispatcher) {
        vm.onTagToggled("kotlin")
        vm.onTagToggled("kotlin")
        assertFalse(vm.filter.value.selectedTags.contains("kotlin"))
    }

    @Test
    fun onTagToggled_multipleTagsAccumulate() = runTest(dispatcher) {
        vm.onTagToggled("kotlin")
        vm.onTagToggled("android")
        val tags = vm.filter.value.selectedTags
        assertTrue(tags.contains("kotlin"))
        assertTrue(tags.contains("android"))
        assertEquals(2, tags.size)
    }

    // -------------------------------------------------------------------------
    // 7. onTagsApplied replaces selected tags wholesale
    // -------------------------------------------------------------------------

    @Test
    fun onTagsApplied_replacesExistingTags() = runTest(dispatcher) {
        vm.onTagToggled("old")
        vm.onTagsApplied(setOf("new1", "new2"))

        val tags = vm.filter.value.selectedTags
        assertFalse(tags.contains("old"))
        assertTrue(tags.contains("new1"))
        assertTrue(tags.contains("new2"))
    }

    @Test
    fun onTagsApplied_emptySetClearsTags() = runTest(dispatcher) {
        vm.onTagToggled("kotlin")
        vm.onTagsApplied(emptySet())
        assertTrue(vm.filter.value.selectedTags.isEmpty())
    }

    // -------------------------------------------------------------------------
    // 8. clearTagFilter clears selected tags, preserves type
    // -------------------------------------------------------------------------

    @Test
    fun clearTagFilter_clearsSelectedTags() = runTest(dispatcher) {
        vm.onTagToggled("kotlin")
        vm.onTagToggled("android")
        vm.onTypeChipToggled("VIDEO")

        vm.clearTagFilter()

        val filter = vm.filter.value
        assertTrue(filter.selectedTags.isEmpty())
        // Type filter should be preserved
        assertEquals(TypeFilter.VIDEO, filter.type)
    }

    // -------------------------------------------------------------------------
    // 9. softDelete calls repository
    // -------------------------------------------------------------------------

    @Test
    fun softDelete_callsRepositorySoftDelete() = runTest(dispatcher) {
        vm.softDelete("tweet-123")
        advanceUntilIdle()
        coVerify { repository.softDelete("tweet-123") }
    }

    // -------------------------------------------------------------------------
    // 10. undoDelete calls repository
    // -------------------------------------------------------------------------

    @Test
    fun undoDelete_callsRepositoryUndoDelete() = runTest(dispatcher) {
        vm.undoDelete("tweet-456")
        advanceUntilIdle()
        coVerify { repository.undoDelete("tweet-456") }
    }

    // -------------------------------------------------------------------------
    // 11. confirmDeletePending calls repository
    // -------------------------------------------------------------------------

    @Test
    fun confirmDeletePending_callsRepository() = runTest(dispatcher) {
        vm.confirmDeletePending("tweet-789")
        advanceUntilIdle()
        coVerify { repository.confirmDeletePending("tweet-789") }
    }

    // -------------------------------------------------------------------------
    // 12. cancelDeletePending calls repository
    // -------------------------------------------------------------------------

    @Test
    fun cancelDeletePending_callsRepository() = runTest(dispatcher) {
        vm.cancelDeletePending("tweet-000")
        advanceUntilIdle()
        coVerify { repository.cancelDeletePending("tweet-000") }
    }

    // -------------------------------------------------------------------------
    // 13. loadTagsForTweet populates tagsForTweet map
    // -------------------------------------------------------------------------

    @Test
    fun loadTagsForTweet_populatesTagsForTweetMap() = runTest(dispatcher) {
        coEvery { repository.getTagsForTweet("tweet-1") } returns listOf("kotlin", "android")

        vm.loadTagsForTweet("tweet-1")
        advanceUntilIdle()

        val tags = vm.tagsForTweet.value["tweet-1"]
        assertEquals(listOf("kotlin", "android"), tags)
    }

    @Test
    fun loadTagsForTweet_mergesToExistingMap() = runTest(dispatcher) {
        coEvery { repository.getTagsForTweet("t1") } returns listOf("a")
        coEvery { repository.getTagsForTweet("t2") } returns listOf("b")

        vm.loadTagsForTweet("t1")
        vm.loadTagsForTweet("t2")
        advanceUntilIdle()

        assertEquals(listOf("a"), vm.tagsForTweet.value["t1"])
        assertEquals(listOf("b"), vm.tagsForTweet.value["t2"])
    }

    // -------------------------------------------------------------------------
    // 14. loadTagsForItems populates batch tags
    // -------------------------------------------------------------------------

    @Test
    fun loadTagsForItems_populatesTagsForMultipleIds() = runTest(dispatcher) {
        val batch = mapOf(
            "t1" to listOf("kotlin"),
            "t2" to listOf("android", "compose"),
        )
        coEvery { repository.getTagsForItems(listOf("t1", "t2")) } returns batch

        vm.loadTagsForItems(listOf("t1", "t2"))
        advanceUntilIdle()

        assertEquals(listOf("kotlin"), vm.tagsForTweet.value["t1"])
        assertEquals(listOf("android", "compose"), vm.tagsForTweet.value["t2"])
    }

    @Test
    fun loadTagsForItems_emptyListDoesNothing() = runTest(dispatcher) {
        vm.loadTagsForItems(emptyList())
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.getTagsForItems(any()) }
        assertTrue(vm.tagsForTweet.value.isEmpty())
    }

    // -------------------------------------------------------------------------
    // 15. loadAllTags populates allTags
    // -------------------------------------------------------------------------

    @Test
    fun loadAllTags_populatesAllTagsFlow() = runTest(dispatcher) {
        coEvery { repository.getAllTags() } returns listOf("kotlin", "android", "compose")

        vm.loadAllTags()
        advanceUntilIdle()

        assertEquals(listOf("kotlin", "android", "compose"), vm.allTags.value)
    }

    // -------------------------------------------------------------------------
    // 16. saveTags calls repository and reloads tags
    // -------------------------------------------------------------------------

    @Test
    fun saveTags_callsRepositoryAndReloads() = runTest(dispatcher) {
        coEvery { repository.getTagsForTweet("t1") } returns listOf("kotlin")
        coEvery { repository.getAllTags() } returns listOf("kotlin")

        vm.saveTags("t1", listOf("kotlin"))
        advanceUntilIdle()

        coVerify { repository.saveTags("t1", listOf("kotlin")) }
        coVerify { repository.getTagsForTweet("t1") }
        coVerify(atLeast = 1) { repository.getAllTags() }
    }

    // -------------------------------------------------------------------------
    // 17. refresh calls repository.refreshBookmarks + syncStatusRepository.refresh
    // -------------------------------------------------------------------------

    @Test
    fun refresh_callsRefreshBookmarksAndSyncStatus() = runTest(dispatcher) {
        vm.refresh()
        advanceUntilIdle()

        coVerify { repository.refreshBookmarks() }
        coVerify { syncStatusRepository.refresh(force = true) }
    }

    // -------------------------------------------------------------------------
    // 18. refreshSyncStatus calls syncStatusRepository.refresh
    // -------------------------------------------------------------------------

    @Test
    fun refreshSyncStatus_callsSyncStatusRepositoryRefresh() = runTest(dispatcher) {
        vm.refreshSyncStatus()
        advanceUntilIdle()

        // Called during init + during refreshSyncStatus
        coVerify(atLeast = 2) { syncStatusRepository.refresh() }
    }

    // -------------------------------------------------------------------------
    // 19. logout calls repository.logout
    // -------------------------------------------------------------------------

    @Test
    fun logout_callsRepositoryLogout() = runTest(dispatcher) {
        vm.logout()
        advanceUntilIdle()
        coVerify { repository.logout() }
    }

    // -------------------------------------------------------------------------
    // 20. disconnectX success emits DisconnectEvent.Success
    // -------------------------------------------------------------------------

    @Test
    fun disconnectX_success_emitsSuccessEvent() = runTest(dispatcher) {
        coEvery { repository.disconnectX() } returns Result.success(Unit)

        val events = mutableListOf<DisconnectEvent>()
        val job = CoroutineScope(dispatcher).launch {
            vm.disconnectEvents.collect { events.add(it) }
        }

        vm.disconnectX()
        advanceUntilIdle()

        assertTrue(
            "Expected Success event, got $events",
            events.any { it is DisconnectEvent.Success },
        )
        job.cancel()
    }

    // -------------------------------------------------------------------------
    // 21. disconnectX failure emits DisconnectEvent.Failure
    // -------------------------------------------------------------------------

    @Test
    fun disconnectX_failure_emitsFailureEvent() = runTest(dispatcher) {
        coEvery { repository.disconnectX() } returns Result.failure(RuntimeException("network error"))

        val events = mutableListOf<DisconnectEvent>()
        val job = CoroutineScope(dispatcher).launch {
            vm.disconnectEvents.collect { events.add(it) }
        }

        vm.disconnectX()
        advanceUntilIdle()

        val failure = events.filterIsInstance<DisconnectEvent.Failure>().firstOrNull()
        assertTrue("Expected Failure event, got $events", failure != null)
        assertEquals("network error", failure!!.reason)
        job.cancel()
    }

    // -------------------------------------------------------------------------
    // 22. filter state is correct after a sequence of mutations
    // -------------------------------------------------------------------------

    @Test
    fun filterState_reflectsSequenceOfMutations() = runTest(dispatcher) {
        // Start from default
        assertEquals(FilterState(), vm.filter.value)

        // Toggle a tag
        vm.onTagToggled("kotlin")
        assertTrue(vm.filter.value.selectedTags.contains("kotlin"))
        assertEquals(TypeFilter.ALL, vm.filter.value.type)

        // Change type
        vm.onTypeChipToggled("ARTICLE")
        assertEquals(TypeFilter.ARTICLE, vm.filter.value.type)
        assertTrue(vm.filter.value.selectedTags.contains("kotlin"))

        // Clear tag filter — type should be preserved
        vm.clearTagFilter()
        assertEquals(TypeFilter.ARTICLE, vm.filter.value.type)
        assertTrue(vm.filter.value.selectedTags.isEmpty())
    }

    // -------------------------------------------------------------------------
    // 23. pagingFlow subscribes to repository.pagingTweetData when collected
    // -------------------------------------------------------------------------

    @Test
    fun pagingFlow_callsRepositoryWhenCollected() = runTest(dispatcher) {
        // Collect pagingFlow to activate flatMapLatest
        val job = CoroutineScope(dispatcher).launch {
            vm.pagingFlow.collect {}
        }
        advanceUntilIdle()

        // The initial (default) FilterState should trigger a pagingTweetData call
        coVerify { repository.pagingTweetData(FilterState()) }

        // Now toggle a tag; flatMapLatest should resubscribe with new filter
        vm.onTagToggled("android")
        advanceUntilIdle()

        val updatedFilter = FilterState(selectedTags = persistentSetOf("android"))
        coVerify { repository.pagingTweetData(updatedFilter) }

        job.cancel()
    }

    // -------------------------------------------------------------------------
    // 24. Inline video — single shared player + lifecycle / viewer state
    // -------------------------------------------------------------------------

    private fun videoBookmark(
        id: String = "v1",
        variants: List<VideoVariant> = listOf(VideoVariant("video/mp4", "https://v/$id.mp4", 2_176_000)),
        videoUrl: String? = "https://v/$id.mp4",
    ) = Bookmark(
        id = id,
        source = BookmarkSource.Twitter,
        author = "@filmmaker",
        title = "video",
        previewText = "v",
        contentType = ContentType.Video,
        savedAt = 0L,
        sourceUrl = "https://x.com/$id",
        videoThumbnailUrl = "https://img/$id.jpg",
        videoVariants = variants,
        videoUrl = videoUrl,
    )

    @Test
    fun playVideo_buildsSingleSharedPlayer_andSetsActiveId() = runTest(dispatcher) {
        assertNull(vm.player)
        assertNull(vm.activeVideoId.value)

        vm.playVideo(videoBookmark("v1"))
        advanceUntilIdle()
        val first = vm.player
        assertNotNull("playVideo should lazily build the shared player", first)
        assertEquals("v1", vm.activeVideoId.value)

        // A second play reuses the same instance — the single-player invariant.
        vm.playVideo(videoBookmark("v2"))
        advanceUntilIdle()
        assertSame("the shared player must be reused, not recreated", first, vm.player)
        assertEquals("v2", vm.activeVideoId.value)
    }

    @Test
    fun playVideo_noPlayableSource_isNoOp() = runTest(dispatcher) {
        vm.playVideo(videoBookmark("v1", variants = emptyList(), videoUrl = null))
        advanceUntilIdle()
        assertNull("no stream → no player built", vm.player)
        assertNull(vm.activeVideoId.value)
    }

    @Test
    fun clearActiveVideo_resetsActiveId() = runTest(dispatcher) {
        vm.playVideo(videoBookmark("v1"))
        advanceUntilIdle()
        assertEquals("v1", vm.activeVideoId.value)

        vm.clearActiveVideo()
        assertNull("scroll-away detaches the active video", vm.activeVideoId.value)
    }

    @Test
    fun expandThenCloseVideoViewer_togglesViewerState() = runTest(dispatcher) {
        // Use a no-stream bookmark so the toggle is exercised without building a player.
        val noStream = videoBookmark("v1", variants = emptyList(), videoUrl = null)
        assertFalse(vm.videoViewerOpen.value)

        vm.expandVideo(noStream)
        advanceUntilIdle()
        assertTrue(vm.videoViewerOpen.value)

        vm.closeVideoViewer()
        assertFalse(vm.videoViewerOpen.value)
    }

    // -------------------------------------------------------------------------
    // 25. Inline video — ON_PAUSE / ON_RESUME lifecycle handling (AC3)
    //
    // The live background/foreground drive is a device gate (deferred); these
    // cover the off-device behaviour: pause-on-background and the resume guard.
    // The positive resume branch needs a player that actually reaches
    // STATE_READY (isPlaying == true), which a Robolectric player without real
    // decode cannot, so it stays in the runtime-evidence deferral.
    // -------------------------------------------------------------------------

    @Test
    fun onAppPaused_pausesActivePlayback() = runTest(dispatcher) {
        vm.playVideo(videoBookmark("v1"))
        advanceUntilIdle()
        val p = vm.player!!
        assertTrue("playVideo starts playback (playWhenReady)", p.playWhenReady)

        vm.onAppPaused()
        assertFalse("ON_PAUSE pauses the shared player", p.playWhenReady)
    }

    @Test
    fun onAppResumed_whenNothingWasPlaying_doesNotForceResume() = runTest(dispatcher) {
        // On a Robolectric player that never reaches STATE_READY, isPlaying is
        // false, so wasPlayingBeforePause is false and ON_RESUME must be a
        // guarded no-op (it must not force playback that was not running).
        vm.playVideo(videoBookmark("v1"))
        advanceUntilIdle()
        vm.onAppPaused()
        assertFalse(vm.player!!.playWhenReady)

        vm.onAppResumed()
        assertFalse("ON_RESUME honours the was-playing guard", vm.player!!.playWhenReady)
    }

    @Test
    fun lifecycleCallbacks_withNoActivePlayer_areNoOps() = runTest(dispatcher) {
        // No video has played → no shared player built → the lifecycle callbacks
        // must short-circuit without building a player or crashing.
        vm.onAppPaused()
        vm.onAppResumed()
        assertNull(vm.player)
    }

    // TEST-3 — onAppResumed positive branch: when wasPlayingBeforePause is true,
    // onAppResumed must call p.play() (sets playWhenReady back to true).
    //
    // Robolectric's ExoPlayer never reaches STATE_READY so p.isPlaying is always
    // false; the only way to exercise the `if (wasPlayingBeforePause) p.play()`
    // branch is to inject the flag directly via reflection.
    @Test
    fun onAppResumed_whenWasPlayingBeforePause_resumesPlayback() = runTest(dispatcher) {
        vm.playVideo(videoBookmark("v1"))
        advanceUntilIdle()
        val p = vm.player!!

        // Pause so the player is in a known state (playWhenReady = false).
        vm.onAppPaused()
        assertFalse("sanity: player is paused", p.playWhenReady)

        // Inject wasPlayingBeforePause = true via reflection to simulate the path
        // that is only reachable when the player is genuinely playing at pause time
        // (p.isPlaying == true), which Robolectric cannot reach without real decode.
        val field = BookmarksViewModel::class.java.getDeclaredField("wasPlayingBeforePause")
        field.isAccessible = true
        field.setBoolean(vm, true)

        vm.onAppResumed()

        assertTrue(
            "ON_RESUME must call play() when wasPlayingBeforePause is true",
            p.playWhenReady,
        )
    }

    // -------------------------------------------------------------------------
    // 26. pagingFlow — filter propagation into repository queries (TEST-5)
    //
    // The existing test verifies the initial call and a tag-toggle resubscription.
    // These tests assert that TYPE filter changes and bulk tag-apply changes also
    // cause pagingTweetData to be called with the updated FilterState, closing
    // the gap where a cached-first-filter regression would pass the original test.
    // -------------------------------------------------------------------------

    @Test
    fun pagingFlow_requeriesRepositoryWithTypeFilter_onTypeChipToggle() = runTest(dispatcher) {
        val job = CoroutineScope(dispatcher).launch {
            vm.pagingFlow.collect {}
        }
        advanceUntilIdle()

        // Baseline: default filter observed on subscription
        coVerify { repository.pagingTweetData(FilterState()) }

        // Toggle to VIDEO type; flatMapLatest must resubscribe with updated filter
        vm.onTypeChipToggled("VIDEO")
        advanceUntilIdle()

        val videoFilter = FilterState(type = TypeFilter.VIDEO)
        coVerify { repository.pagingTweetData(videoFilter) }

        job.cancel()
    }

    @Test
    fun pagingFlow_requeriesRepositoryWithTagFilter_onTagsApplied() = runTest(dispatcher) {
        val job = CoroutineScope(dispatcher).launch {
            vm.pagingFlow.collect {}
        }
        advanceUntilIdle()

        // Apply a batch of tags wholesale; flatMapLatest must resubscribe with them
        vm.onTagsApplied(setOf("compose", "kotlin"))
        advanceUntilIdle()

        // Verify that pagingTweetData was called with a state that contains both tags
        coVerify {
            repository.pagingTweetData(
                match { state ->
                    state.selectedTags.containsAll(listOf("compose", "kotlin")) &&
                        state.selectedTags.size == 2
                },
            )
        }

        job.cancel()
    }

    @Test
    fun pagingFlow_requeriesRepositoryWithCombinedFilter_onTypeAndTagChange() = runTest(dispatcher) {
        val job = CoroutineScope(dispatcher).launch {
            vm.pagingFlow.collect {}
        }
        advanceUntilIdle()

        // Apply both type and tag; each mutation triggers a separate call
        vm.onTypeChipToggled("ARTICLE")
        advanceUntilIdle()
        vm.onTagToggled("android")
        advanceUntilIdle()

        val combinedFilter = FilterState(
            type = TypeFilter.ARTICLE,
            selectedTags = persistentSetOf("android"),
        )
        coVerify { repository.pagingTweetData(combinedFilter) }

        job.cancel()
    }

    // -------------------------------------------------------------------------
    // 27. Delta loading + overwrite (R2-PERF-05 / R2-CR-6)
    //
    // A paging append re-runs loadTagsForItems with the full accumulated id set.
    // Only the new (delta) ids — plus any edited (dirty) id — must be re-queried,
    // and the merge must overwrite so an emptied tag set clears its chips.
    // -------------------------------------------------------------------------

    // Force an id into the private dirty set so the overwrite path can be exercised
    // in isolation (mirrors the wasPlayingBeforePause reflection precedent above).
    private fun markTweetDirty(id: String) {
        val field = BookmarksViewModel::class.java.getDeclaredField("_dirtyTweetIds")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(vm) as MutableSet<String>).add(id)
    }

    @Test
    fun loadTagsForItems_deltaOnly_queriesOnlyNewIds() = runTest(dispatcher) {
        coEvery { repository.getTagsForItems(listOf("t1", "t2")) } returns mapOf(
            "t1" to listOf("kotlin"),
            "t2" to emptyList(),
        )
        coEvery { repository.getTagsForItems(listOf("t3")) } returns mapOf("t3" to listOf("compose"))

        // First snapshot loads t1 + t2.
        vm.loadTagsForItems(listOf("t1", "t2"))
        advanceUntilIdle()

        // Next snapshot appends t3 — only the delta [t3] must hit the repository.
        vm.loadTagsForItems(listOf("t1", "t2", "t3"))
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getTagsForItems(listOf("t1", "t2")) }
        coVerify(exactly = 1) { repository.getTagsForItems(listOf("t3")) }
        // The full accumulated set must never be re-queried wholesale.
        coVerify(exactly = 0) { repository.getTagsForItems(listOf("t1", "t2", "t3")) }
    }

    @Test
    fun loadTagsForItems_alreadyLoadedIds_skipRepositoryEntirely() = runTest(dispatcher) {
        coEvery { repository.getTagsForItems(listOf("t1")) } returns mapOf("t1" to listOf("kotlin"))

        vm.loadTagsForItems(listOf("t1"))
        advanceUntilIdle()
        // Re-issuing the identical snapshot (no new ids, nothing dirty) must short-circuit.
        vm.loadTagsForItems(listOf("t1"))
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getTagsForItems(listOf("t1")) }
    }

    @Test
    fun loadTagsForItems_overwrite_clearsStaleChips() = runTest(dispatcher) {
        coEvery { repository.getTagsForItems(listOf("t1")) } returns mapOf("t1" to listOf("kotlin"))
        vm.loadTagsForItems(listOf("t1"))
        advanceUntilIdle()
        assertEquals(listOf("kotlin"), vm.tagsForTweet.value["t1"])

        // The tag was removed; the repository now returns an explicit empty entry. Mark
        // t1 dirty so the delta guard re-queries it, then assert the chips clear (the
        // map entry is overwritten with the empty list, not unioned with the stale one).
        markTweetDirty("t1")
        coEvery { repository.getTagsForItems(listOf("t1")) } returns mapOf("t1" to emptyList())
        vm.loadTagsForItems(listOf("t1"))
        advanceUntilIdle()

        assertEquals(emptyList<String>(), vm.tagsForTweet.value["t1"])
    }

    @Test
    fun saveTags_invalidatesId_causingReloadOnNextBatch() = runTest(dispatcher) {
        coEvery { repository.getTagsForItems(listOf("t1")) } returns mapOf("t1" to listOf("kotlin"))
        coEvery { repository.getTagsForTweet("t1") } returns listOf("kotlin")
        coEvery { repository.getAllTags() } returns listOf("kotlin")

        // Initial batch load marks t1 as loaded.
        vm.loadTagsForItems(listOf("t1"))
        advanceUntilIdle()

        // Editing tags marks t1 dirty so the next identical snapshot re-queries it
        // instead of skipping it as already-loaded.
        vm.saveTags("t1", listOf("kotlin"))
        advanceUntilIdle()
        vm.loadTagsForItems(listOf("t1"))
        advanceUntilIdle()

        coVerify(exactly = 2) { repository.getTagsForItems(listOf("t1")) }
    }
}
