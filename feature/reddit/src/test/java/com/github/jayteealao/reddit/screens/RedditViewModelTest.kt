package com.github.jayteealao.reddit.screens

import com.github.jayteealao.crumbs.data.FilterState
import com.github.jayteealao.crumbs.data.TagRepository
import com.github.jayteealao.crumbs.data.TypeFilter
import com.github.jayteealao.reddit.data.RedditPrefs
import com.github.jayteealao.reddit.data.RedditRepository
import com.github.jayteealao.reddit.services.RedditAuthClient
import com.github.jayteealao.reddit.services.RedditApiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [RedditViewModel].
 *
 * Uses [StandardTestDispatcher] for deterministic coroutine scheduling.
 * All dependencies are [mockk] fakes.
 *
 * Notes on coverage:
 *  - [_isAccessTokenAvailable] is driven by [RedditPrefs.accessToken] which is
 *    mocked as a StateFlow; checkAccessToken() reads the first emission in init.
 *  - Filter state ([_filter]) is a plain [MutableStateFlow] so its value is
 *    readable immediately after mutation — no WhileSubscribed activation needed.
 *  - [pagingFlow] delegates to [RedditRepository.pagingPostsData] which is
 *    mocked; correctness of the underlying PagingSource is tested separately.
 *  - Tag operations delegate to [TagRepository] (the [RedditRepository]
 *    implementation is @RedditTags-qualified at injection time).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RedditViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var redditRepository: RedditRepository
    private lateinit var redditAuthClient: RedditAuthClient
    private lateinit var redditApiService: RedditApiService
    private lateinit var redditPrefs: RedditPrefs
    private lateinit var tagRepository: TagRepository
    private lateinit var vm: RedditViewModel

    // Backing StateFlow for accessToken — controls the token observed by checkAccessToken()
    private val accessTokenFlow = MutableStateFlow("")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        redditRepository = mockk(relaxed = true)
        redditAuthClient = mockk(relaxed = true)
        redditApiService = mockk(relaxed = true)
        redditPrefs = mockk(relaxed = true)
        tagRepository = mockk(relaxed = true)

        // Wire mock flows
        every { redditPrefs.accessToken } returns accessTokenFlow

        // pagingPostsData must return a real Flow (relaxed returns null which crashes cachedIn)
        every { redditRepository.pagingPostsData(any()) } returns MutableSharedFlow(replay = 0)

        vm = RedditViewModel(
            redditRepository = redditRepository,
            redditAuthClient = redditAuthClient,
            redditApiService = redditApiService,
            redditPrefs = redditPrefs,
            tagRepository = tagRepository,
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
    fun initialIsAccessTokenAvailable_isFalse_whenTokenIsBlank() = runTest(dispatcher) {
        // accessTokenFlow starts as "", so checkAccessToken() should leave it false
        advanceUntilIdle()
        assertFalse(vm.isAccessTokenAvailable.value)
    }

    @Test
    fun initialUsername_isEmpty() = runTest(dispatcher) {
        advanceUntilIdle()
        assertEquals("", vm.username.value)
    }

    @Test
    fun initialAllTags_isEmpty() = runTest(dispatcher) {
        // Before coroutines drain allTags stays at default empty list
        assertTrue(vm.allTags.value.isEmpty())
    }

    @Test
    fun initialTagsForTweet_isEmpty() = runTest(dispatcher) {
        advanceUntilIdle()
        assertTrue(vm.tagsForTweet.value.isEmpty())
    }

    // -------------------------------------------------------------------------
    // 2. init calls buildDatabase on repository
    // -------------------------------------------------------------------------

    @Test
    fun init_callsBuildDatabase() = runTest(dispatcher) {
        advanceUntilIdle()
        // buildDatabase is called once during init (relaxed mock records call)
        coVerify(atLeast = 1) { redditRepository.buildDatabase() }
    }

    // -------------------------------------------------------------------------
    // 3. checkAccessToken — token present → isAccessTokenAvailable = true
    // -------------------------------------------------------------------------

    @Test
    fun checkAccessToken_setsTrue_whenTokenIsPresent() = runTest(dispatcher) {
        // Arrange: emit a non-blank token before the ViewModel reads it
        accessTokenFlow.value = "valid-access-token"

        // Re-create ViewModel so its init block sees the non-blank token
        val freshVm = RedditViewModel(
            redditRepository = redditRepository,
            redditAuthClient = redditAuthClient,
            redditApiService = redditApiService,
            redditPrefs = redditPrefs,
            tagRepository = tagRepository,
        )
        advanceUntilIdle()

        assertTrue(freshVm.isAccessTokenAvailable.value)
    }

    // -------------------------------------------------------------------------
    // 4. getAccessToken — success sets isAccessTokenAvailable = true
    // -------------------------------------------------------------------------

    @Test
    fun getAccessToken_success_setsIsAccessTokenAvailable() = runTest(dispatcher) {
        coEvery { redditAuthClient.getAccessToken("auth-code") } returns "new-token"

        vm.getAccessToken("auth-code")
        advanceUntilIdle()

        assertTrue(vm.isAccessTokenAvailable.value)
    }

    @Test
    fun getAccessToken_null_doesNotSetIsAccessTokenAvailable() = runTest(dispatcher) {
        coEvery { redditAuthClient.getAccessToken("bad-code") } returns null

        vm.getAccessToken("bad-code")
        advanceUntilIdle()

        assertFalse(vm.isAccessTokenAvailable.value)
    }

    @Test
    fun getAccessToken_success_callsBuildDatabase() = runTest(dispatcher) {
        coEvery { redditAuthClient.getAccessToken("auth-code") } returns "new-token"

        vm.getAccessToken("auth-code")
        advanceUntilIdle()

        // Called at least twice: once during init and once after getAccessToken
        coVerify(atLeast = 2) { redditRepository.buildDatabase() }
    }

    // -------------------------------------------------------------------------
    // 5. buildDatabase delegates to repository
    // -------------------------------------------------------------------------

    @Test
    fun buildDatabase_delegatesToRepository() = runTest(dispatcher) {
        vm.buildDatabase()
        advanceUntilIdle()
        // Called at least twice: init + explicit call
        coVerify(atLeast = 2) { redditRepository.buildDatabase() }
    }

    // -------------------------------------------------------------------------
    // 6. onTypeChipToggled
    // -------------------------------------------------------------------------

    @Test
    fun onTypeChipToggled_updatesFilterType() = runTest(dispatcher) {
        assertEquals(TypeFilter.ALL, vm.filter.value.type)

        vm.onTypeChipToggled("VIDEO")
        assertEquals(TypeFilter.VIDEO, vm.filter.value.type)
    }

    @Test
    fun onTypeChipToggled_unknownId_defaultsToAll() = runTest(dispatcher) {
        vm.onTypeChipToggled("VIDEO")
        assertEquals(TypeFilter.VIDEO, vm.filter.value.type)

        vm.onTypeChipToggled("NOT_A_TYPE")
        // runCatching in ViewModel defaults to TypeFilter.ALL on unknown names
        assertEquals(TypeFilter.ALL, vm.filter.value.type)
    }

    // -------------------------------------------------------------------------
    // 7. onTagToggled
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
    // 8. onTagsApplied
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
    // 9. clearTagFilter
    // -------------------------------------------------------------------------

    @Test
    fun clearTagFilter_clearsSelectedTags_preservesType() = runTest(dispatcher) {
        vm.onTagToggled("kotlin")
        vm.onTagToggled("android")
        vm.onTypeChipToggled("VIDEO")

        vm.clearTagFilter()

        val filter = vm.filter.value
        assertTrue(filter.selectedTags.isEmpty())
        assertEquals(TypeFilter.VIDEO, filter.type)
    }

    // -------------------------------------------------------------------------
    // 10. softDelete delegates to repository
    // -------------------------------------------------------------------------

    @Test
    fun softDelete_callsRepository() = runTest(dispatcher) {
        vm.softDelete("post-123")
        advanceUntilIdle()
        coVerify { redditRepository.softDelete("post-123") }
    }

    // -------------------------------------------------------------------------
    // 11. undoDelete delegates to repository
    // -------------------------------------------------------------------------

    @Test
    fun undoDelete_callsRepository() = runTest(dispatcher) {
        vm.undoDelete("post-456")
        advanceUntilIdle()
        coVerify { redditRepository.undoDelete("post-456") }
    }

    // -------------------------------------------------------------------------
    // 12. loadTagsForTweet
    // -------------------------------------------------------------------------

    @Test
    fun loadTagsForTweet_populatesTagsMap() = runTest(dispatcher) {
        coEvery { tagRepository.getTagsForTweet("post-1") } returns listOf("kotlin", "android")

        vm.loadTagsForTweet("post-1")
        advanceUntilIdle()

        assertEquals(listOf("kotlin", "android"), vm.tagsForTweet.value["post-1"])
    }

    @Test
    fun loadTagsForTweet_mergesMultipleEntries() = runTest(dispatcher) {
        coEvery { tagRepository.getTagsForTweet("p1") } returns listOf("a")
        coEvery { tagRepository.getTagsForTweet("p2") } returns listOf("b")

        vm.loadTagsForTweet("p1")
        vm.loadTagsForTweet("p2")
        advanceUntilIdle()

        assertEquals(listOf("a"), vm.tagsForTweet.value["p1"])
        assertEquals(listOf("b"), vm.tagsForTweet.value["p2"])
    }

    // -------------------------------------------------------------------------
    // 13. loadTagsForItems
    // -------------------------------------------------------------------------

    @Test
    fun loadTagsForItems_populatesBatchTags() = runTest(dispatcher) {
        val batch = mapOf(
            "p1" to listOf("kotlin"),
            "p2" to listOf("android", "compose"),
        )
        coEvery { tagRepository.getTagsForItems(listOf("p1", "p2")) } returns batch

        vm.loadTagsForItems(listOf("p1", "p2"))
        advanceUntilIdle()

        assertEquals(listOf("kotlin"), vm.tagsForTweet.value["p1"])
        assertEquals(listOf("android", "compose"), vm.tagsForTweet.value["p2"])
    }

    @Test
    fun loadTagsForItems_emptyList_doesNothing() = runTest(dispatcher) {
        vm.loadTagsForItems(emptyList())
        advanceUntilIdle()

        coVerify(exactly = 0) { tagRepository.getTagsForItems(any()) }
        assertTrue(vm.tagsForTweet.value.isEmpty())
    }

    // -------------------------------------------------------------------------
    // 14. loadAllTags
    // -------------------------------------------------------------------------

    @Test
    fun loadAllTags_populatesAllTagsFlow() = runTest(dispatcher) {
        coEvery { tagRepository.getAllTags() } returns listOf("kotlin", "android", "compose")

        vm.loadAllTags()
        advanceUntilIdle()

        assertEquals(listOf("kotlin", "android", "compose"), vm.allTags.value)
    }

    // -------------------------------------------------------------------------
    // 15. saveTags — delegates and reloads
    // -------------------------------------------------------------------------

    @Test
    fun saveTags_callsRepositoryAndReloads() = runTest(dispatcher) {
        coEvery { tagRepository.getTagsForTweet("p1") } returns listOf("kotlin")
        coEvery { tagRepository.getAllTags() } returns listOf("kotlin")

        vm.saveTags("p1", listOf("kotlin"))
        advanceUntilIdle()

        coVerify { tagRepository.saveTags("p1", listOf("kotlin")) }
        coVerify { tagRepository.getTagsForTweet("p1") }
        coVerify(atLeast = 1) { tagRepository.getAllTags() }
    }

    // -------------------------------------------------------------------------
    // 16. logout clears token flag and username
    // -------------------------------------------------------------------------

    @Test
    fun logout_callsRepositoryLogout() = runTest(dispatcher) {
        vm.logout()
        advanceUntilIdle()
        coVerify { redditRepository.logout() }
    }

    @Test
    fun logout_clearsIsAccessTokenAvailable() = runTest(dispatcher) {
        // Simulate having been logged in
        coEvery { redditAuthClient.getAccessToken("code") } returns "tok"
        vm.getAccessToken("code")
        advanceUntilIdle()
        assertTrue(vm.isAccessTokenAvailable.value)

        vm.logout()
        advanceUntilIdle()
        assertFalse(vm.isAccessTokenAvailable.value)
    }

    @Test
    fun logout_clearsUsername() = runTest(dispatcher) {
        vm.logout()
        advanceUntilIdle()
        assertEquals("", vm.username.value)
    }

    // -------------------------------------------------------------------------
    // 17. filter state — sequence of mutations
    // -------------------------------------------------------------------------

    @Test
    fun filterState_correctAfterSequenceOfMutations() = runTest(dispatcher) {
        assertEquals(FilterState(), vm.filter.value)

        vm.onTagToggled("kotlin")
        assertTrue(vm.filter.value.selectedTags.contains("kotlin"))
        assertEquals(TypeFilter.ALL, vm.filter.value.type)

        vm.onTypeChipToggled("ARTICLE")
        assertEquals(TypeFilter.ARTICLE, vm.filter.value.type)
        assertTrue(vm.filter.value.selectedTags.contains("kotlin"))

        vm.clearTagFilter()
        assertEquals(TypeFilter.ARTICLE, vm.filter.value.type)
        assertTrue(vm.filter.value.selectedTags.isEmpty())
    }

    // -------------------------------------------------------------------------
    // 18. pagingFlow subscribes to repository when collected
    // -------------------------------------------------------------------------

    @Test
    fun pagingFlow_callsRepositoryWhenCollected() = runTest(dispatcher) {
        val job: Job = CoroutineScope(dispatcher).launch { vm.pagingFlow.collect {} }
        advanceUntilIdle()

        // The initial default FilterState should trigger a pagingPostsData call
        coVerify { redditRepository.pagingPostsData(FilterState()) }

        job.cancel()
    }

    // -------------------------------------------------------------------------
    // 19. pagingFlow re-subscribes on filter change
    // -------------------------------------------------------------------------

    @Test
    fun pagingFlow_resubscribesOnFilterChange() = runTest(dispatcher) {
        val job: Job = CoroutineScope(dispatcher).launch { vm.pagingFlow.collect {} }
        advanceUntilIdle()

        vm.onTagToggled("android")
        advanceUntilIdle()

        val expectedFilter = FilterState(selectedTags = persistentSetOf("android"))
        coVerify { redditRepository.pagingPostsData(expectedFilter) }

        job.cancel()
    }
}
