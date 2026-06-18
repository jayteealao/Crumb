package com.github.jayteealao.crumbs.screens

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.github.jayteealao.crumbs.data.SearchRepository
import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.models.ContentType
import com.github.jayteealao.pref.clearRecentSearches
import com.github.jayteealao.pref.recentSearches
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [SearchViewModel] — the debounced FTS search state machine.
 *
 * Uses [StandardTestDispatcher] so all coroutines are executed explicitly via
 * [advanceTimeBy] / [advanceUntilIdle], making time-dependent debounce
 * assertions deterministic.
 *
 * DataStore is backed by the real Robolectric application context. Tests that
 * verify DataStore writes use `withContext(Dispatchers.Default)` + `withTimeout`
 * so the assertion waits on real I/O rather than virtual time.  The DataStore
 * is cleared in [setUp] via a blocking call to prevent cross-test contamination
 * (Robolectric reuses the same application context across tests in a class).
 *
 * Important: [SearchViewModel.uiState] and [SearchViewModel.recentSearches] use
 * [kotlinx.coroutines.flow.SharingStarted.WhileSubscribed], which only activates
 * upstream collection while there is at least one subscriber. Each test that
 * reads these flows must activate them with [activateUiState] or
 * [activateRecentSearches] before exercising the ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var searchRepository: SearchRepository
    private lateinit var vm: SearchViewModel

    /** Keeps uiState and recentSearches active (WhileSubscribed). */
    private var uiStateCollector: Job? = null
    private var recentSearchesCollector: Job? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        // Clear any DataStore state left by a previous test in this JVM process.
        // Robolectric shares the application context across all tests in a class,
        // so without this an earlier onSearchSubmitted write would bleed through.
        runBlocking { context.clearRecentSearches() }
        searchRepository = mockk()
        vm = SearchViewModel(
            context = context,
            searchRepository = searchRepository,
            savedStateHandle = SavedStateHandle(),
        )
    }

    @After
    fun tearDown() {
        uiStateCollector?.cancel()
        recentSearchesCollector?.cancel()
        Dispatchers.resetMain()
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun bookmark(id: String) = Bookmark(
        id = id,
        source = BookmarkSource.Twitter,
        author = "@test",
        title = "Title $id",
        previewText = "Preview $id",
        contentType = ContentType.Text,
        savedAt = 1_700_000_000_000L,
        sourceUrl = "https://example.com/$id",
    )

    /**
     * Subscribe to [SearchViewModel.uiState] so [SharingStarted.WhileSubscribed]
     * activates upstream collection.
     */
    private fun activateUiState() {
        uiStateCollector = vm.uiState
            .onEach {}
            .launchIn(kotlinx.coroutines.CoroutineScope(dispatcher))
    }

    /**
     * Subscribe to [SearchViewModel.recentSearches] so [SharingStarted.WhileSubscribed]
     * activates upstream DataStore collection.
     */
    private fun activateRecentSearches() {
        recentSearchesCollector = vm.recentSearches
            .onEach {}
            .launchIn(kotlinx.coroutines.CoroutineScope(dispatcher))
    }

    /**
     * Wait for the first [context.recentSearches()] emission that satisfies
     * [predicate] using real wall-clock time. DataStore writes run on a
     * background I/O dispatcher, so we must leave the virtual-time scheduler
     * via [withContext(Dispatchers.Default)] to let real suspension happen.
     */
    private suspend fun awaitRecentSearches(
        timeoutMs: Long = 10_000L,
        predicate: (List<String>) -> Boolean,
    ): List<String> = withContext(Dispatchers.Default) {
        withTimeout(timeoutMs) {
            context.recentSearches().filter(predicate).first()
        }
    }

    // -----------------------------------------------------------------------
    // 1. idle_whenQueryIsBlank
    // -----------------------------------------------------------------------

    @Test
    fun idle_whenQueryIsBlank() = runTest(dispatcher) {
        activateUiState()
        advanceUntilIdle()

        assertEquals(SearchUiState.Idle, vm.uiState.value)
    }

    @Test
    fun idle_afterQueryClearedToBlank() = runTest(dispatcher) {
        val hits = MutableSharedFlow<List<Bookmark>>(replay = 1)
        hits.emit(listOf(bookmark("b1")))
        every { searchRepository.search(any()) } returns hits

        activateUiState()

        vm.onQueryChanged("kotlin")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        advanceUntilIdle()

        vm.onQueryChanged("")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        advanceUntilIdle()

        assertEquals(SearchUiState.Idle, vm.uiState.value)
    }

    // -----------------------------------------------------------------------
    // 2. loading_then_results_whenFtsReturnsHits
    // -----------------------------------------------------------------------

    @Test
    fun loading_then_results_whenFtsReturnsHits() = runTest(dispatcher) {
        // replay=0: Loading stays visible until we explicitly emit into hitsFlow.
        val hitsFlow = MutableSharedFlow<List<Bookmark>>(replay = 0)
        every { searchRepository.search(any()) } returns hitsFlow

        activateUiState()
        advanceUntilIdle()
        assertEquals("initial state should be Idle", SearchUiState.Idle, vm.uiState.value)

        vm.onQueryChanged("compose")

        // Cross the 300 ms debounce boundary — flatMapLatest subscribes to
        // hitsFlow; onStart emits Loading before any item arrives.
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        advanceUntilIdle()
        assertEquals(SearchUiState.Loading("compose"), vm.uiState.value)

        // Emit hits from the repository → Results
        hitsFlow.emit(listOf(bookmark("1"), bookmark("2")))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("expected Results, got $state", state is SearchUiState.Results)
        val results = state as SearchUiState.Results
        assertEquals("compose", results.query)
        assertEquals(2, results.hits.size)
    }

    // -----------------------------------------------------------------------
    // 3. empty_whenFtsReturnsNoHits
    // -----------------------------------------------------------------------

    @Test
    fun empty_whenFtsReturnsNoHits() = runTest(dispatcher) {
        every { searchRepository.search(any()) } returns flowOf(emptyList())

        activateUiState()

        vm.onQueryChanged("noresults")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        advanceUntilIdle()

        assertEquals(SearchUiState.Empty("noresults"), vm.uiState.value)
    }

    // -----------------------------------------------------------------------
    // 4. debounce_suppressesIntermediateValues
    // -----------------------------------------------------------------------

    @Test
    fun debounce_suppressesIntermediateValues() = runTest(dispatcher) {
        every { searchRepository.search(any()) } returns flowOf(listOf(bookmark("x")))

        activateUiState()
        advanceUntilIdle()

        // Rapid-fire three queries within the debounce window (150 ms total < 300 ms)
        vm.onQueryChanged("k")
        advanceTimeBy(50)
        vm.onQueryChanged("ko")
        advanceTimeBy(50)
        vm.onQueryChanged("kot")
        advanceTimeBy(50)

        verify(exactly = 0) { searchRepository.search(any()) }

        // Settle on the last value "kot"
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        advanceUntilIdle()

        // Exactly ONE call to the repository — for the final debounced value only
        verify(exactly = 1) { searchRepository.search(any()) }
    }

    // -----------------------------------------------------------------------
    // 5. onSearchSubmitted_recordsRecentSearch
    // -----------------------------------------------------------------------

    @Test
    fun onSearchSubmitted_recordsRecentSearch() = runTest(dispatcher) {
        every { searchRepository.search(any()) } returns flowOf(emptyList())

        activateUiState()
        advanceUntilIdle()

        vm.onQueryChanged("kotlin flows")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        advanceUntilIdle()

        vm.onSearchSubmitted()
        advanceUntilIdle()

        // DataStore writes land on a real I/O thread. Use withContext(Default) +
        // withTimeout to leave the virtual-time scheduler and wait on real I/O.
        val recents = awaitRecentSearches { it.contains("kotlin flows") }
        assertTrue(
            "Expected 'kotlin flows' in recents $recents",
            recents.contains("kotlin flows"),
        )
    }

    // -----------------------------------------------------------------------
    // 6. onSearchSubmitted_ignoresEmptyQuery
    // -----------------------------------------------------------------------

    @Test
    fun onSearchSubmitted_ignoresEmptyQuery() = runTest(dispatcher) {
        // Query is blank (default "") — onSearchSubmitted returns early
        vm.onSearchSubmitted()
        advanceUntilIdle()

        // Read current DataStore value in real time; list must stay empty.
        val recents = withContext(Dispatchers.Default) {
            context.recentSearches().first()
        }
        assertTrue("Expected empty recents when query is blank, got $recents", recents.isEmpty())
    }

    @Test
    fun onSearchSubmitted_ignoresWhitespaceOnlyQuery() = runTest(dispatcher) {
        activateUiState()
        advanceUntilIdle()

        // Whitespace-only queries satisfy isBlank() so onSearchSubmitted trims
        // to "" and exits without writing to DataStore.
        vm.onQueryChanged("   ")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        advanceUntilIdle()

        vm.onSearchSubmitted()
        advanceUntilIdle()

        val recents = withContext(Dispatchers.Default) {
            context.recentSearches().first()
        }
        assertTrue("Expected empty recents for whitespace-only query, got $recents", recents.isEmpty())
    }

    // -----------------------------------------------------------------------
    // 7. onRecentSearchSelected_setsQueryAndTriggersSearch
    // -----------------------------------------------------------------------

    @Test
    fun onRecentSearchSelected_setsQueryAndTriggersSearch() = runTest(dispatcher) {
        every { searchRepository.search(any()) } returns flowOf(listOf(bookmark("r1")))

        activateUiState()
        advanceUntilIdle()

        vm.onRecentSearchSelected("compose")

        // Query StateFlow updates immediately — no debounce on this path
        assertEquals("compose", vm.query.value)

        // After the debounce window the repository is called and Results appear
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("expected Results after recent search selected, got $state", state is SearchUiState.Results)
        assertEquals("compose", (state as SearchUiState.Results).query)
        assertFalse("hits should be non-empty", state.hits.isEmpty())
    }

    @Test
    fun onRecentSearchSelected_replacesPreviousQuery() = runTest(dispatcher) {
        every { searchRepository.search(any()) } returns flowOf(emptyList())

        activateUiState()
        advanceUntilIdle()

        vm.onQueryChanged("oldquery")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        advanceUntilIdle()

        vm.onRecentSearchSelected("newquery")
        assertEquals("newquery", vm.query.value)
        assertNotEquals("oldquery", vm.query.value)
    }
}
