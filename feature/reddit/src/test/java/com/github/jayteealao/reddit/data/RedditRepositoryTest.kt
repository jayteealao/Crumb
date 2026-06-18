package com.github.jayteealao.reddit.data

import com.github.jayteealao.crumbs.data.DeletedBookmarkRepository
import com.github.jayteealao.crumbs.data.SyncErrorBus
import com.github.jayteealao.reddit.models.RedditTagCrossRef
import com.github.jayteealao.reddit.services.RedditApiService
import com.github.jayteealao.reddit.services.RedditAuthClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [RedditRepository.getTagsForItems] — parity with the Twitter
 * `RepositoryTest`. This is the layer that issues the `WHERE postId IN (:ids)` query,
 * so it owns the SQLite host-parameter guard (R2-CR-6) and the zero-tag empty-entry
 * injection (R2-PERF-05 overwrite contract) for the Reddit tab.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RedditRepositoryTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var redditDao: RedditDao
    private lateinit var redditApiService: RedditApiService
    private lateinit var redditAuthClient: RedditAuthClient
    private lateinit var redditPrefs: RedditPrefs
    private lateinit var deletedBookmarkRepository: DeletedBookmarkRepository
    private lateinit var syncErrorBus: SyncErrorBus
    private lateinit var scope: CoroutineScope
    private lateinit var repository: RedditRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        redditDao = mockk(relaxed = true)
        redditApiService = mockk(relaxed = true)
        redditAuthClient = mockk(relaxed = true)
        redditPrefs = mockk(relaxed = true)
        deletedBookmarkRepository = mockk(relaxed = true)
        syncErrorBus = mockk(relaxed = true)
        scope = CoroutineScope(dispatcher)

        repository = RedditRepository(
            redditDao,
            redditApiService,
            redditAuthClient,
            redditPrefs,
            deletedBookmarkRepository,
            syncErrorBus,
            scope,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun getTagsForItems_chunksIdsAtMost900PerDaoQuery() = runTest(dispatcher) {
        val ids = (1..950).map { "p$it" }
        val chunks = mutableListOf<List<String>>()
        coEvery { redditDao.getTagsForRedditPosts(capture(chunks)) } returns emptyList()

        val result = repository.getTagsForItems(ids)

        assertEquals(2, chunks.size)
        assertTrue("each chunk must stay within the 900-param guard", chunks.all { it.size <= 900 })
        assertEquals(listOf(900, 50), chunks.map { it.size })
        assertEquals(950, result.size)
        assertTrue(result.values.all { it.isEmpty() })
    }

    @Test
    fun getTagsForItems_groupsRowsAndInjectsEmptyEntryForZeroTagIds() = runTest(dispatcher) {
        coEvery { redditDao.getTagsForRedditPosts(listOf("a", "b", "c")) } returns listOf(
            RedditTagCrossRef("a", "kotlin"),
            RedditTagCrossRef("a", "android"),
            RedditTagCrossRef("b", "compose"),
        )

        val result = repository.getTagsForItems(listOf("a", "b", "c"))

        assertEquals(listOf("kotlin", "android"), result["a"])
        assertEquals(listOf("compose"), result["b"])
        assertEquals(emptyList<String>(), result["c"])
    }

    @Test
    fun getTagsForItems_emptyInput_returnsEmptyMapWithoutQuerying() = runTest(dispatcher) {
        val result = repository.getTagsForItems(emptyList())
        assertTrue(result.isEmpty())
    }
}
