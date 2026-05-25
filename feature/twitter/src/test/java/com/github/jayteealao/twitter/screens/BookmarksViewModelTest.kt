package com.github.jayteealao.twitter.screens

import androidx.lifecycle.SavedStateHandle
import com.github.jayteealao.crumbs.data.FilterState
import com.github.jayteealao.crumbs.data.TypeFilter
import com.github.jayteealao.twitter.data.Repository
import com.github.jayteealao.twitter.data.SnackbarEvent
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
import org.junit.Assert.assertNull
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
    private val snackbarEventsFlow = MutableSharedFlow<SnackbarEvent>(replay = 0, extraBufferCapacity = 4)

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
}
