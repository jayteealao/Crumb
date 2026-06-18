package com.github.jayteealao.twitter.data

import com.github.jayteealao.crumbs.data.DeletedBookmarkRepository
import com.github.jayteealao.twitter.data.firestore.FirestoreRepository
import com.github.jayteealao.twitter.models.TweetTagCrossRef
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
 * Unit tests for [Repository.getTagsForItems] — the layer that actually issues the
 * `WHERE tweetId IN (:ids)` query and therefore owns the SQLite host-parameter guard
 * (R2-CR-6) and the zero-tag empty-entry injection (R2-PERF-05 overwrite contract).
 *
 * Only the [TweetDao] interaction matters here; every other collaborator is a relaxed
 * fake and the singleton's background init coroutine is harmless (it reads
 * [TweetDao.getLatestBookmark], which the relaxed mock answers with null).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var tweetDao: TweetDao
    private lateinit var authPref: Prefs
    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var deletedBookmarkRepository: DeletedBookmarkRepository
    private lateinit var callableService: TwitterCallableService
    private lateinit var syncEnqueuer: TwitterSyncEnqueuer
    private lateinit var scope: CoroutineScope
    private lateinit var repository: Repository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        tweetDao = mockk(relaxed = true)
        authPref = mockk(relaxed = true)
        firestoreRepository = mockk(relaxed = true)
        deletedBookmarkRepository = mockk(relaxed = true)
        callableService = mockk(relaxed = true)
        syncEnqueuer = mockk(relaxed = true)
        // isRefreshing combines this flow at construction time; a real flow keeps the
        // combine() operator from tripping over a relaxed-mock return value.
        every { syncEnqueuer.observeIsRunning() } returns flowOf(false)
        scope = CoroutineScope(dispatcher)

        repository = Repository(
            tweetDao,
            authPref,
            firestoreRepository,
            deletedBookmarkRepository,
            callableService,
            scope,
            syncEnqueuer,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun getTagsForItems_chunksIdsAtMost900PerDaoQuery() = runTest(dispatcher) {
        val ids = (1..950).map { "id$it" }
        val chunks = mutableListOf<List<String>>()
        coEvery { tweetDao.getTagsForTweets(capture(chunks)) } returns emptyList()

        val result = repository.getTagsForItems(ids)

        // 950 ids → two DAO queries (900 + 50); neither may exceed the 900-param guard.
        assertEquals(2, chunks.size)
        assertTrue("each chunk must stay within the 900-param guard", chunks.all { it.size <= 900 })
        assertEquals(listOf(900, 50), chunks.map { it.size })
        // Every requested id is represented with an explicit (here empty) entry.
        assertEquals(950, result.size)
        assertTrue(result.values.all { it.isEmpty() })
    }

    @Test
    fun getTagsForItems_groupsRowsAndInjectsEmptyEntryForZeroTagIds() = runTest(dispatcher) {
        coEvery { tweetDao.getTagsForTweets(listOf("a", "b", "c")) } returns listOf(
            TweetTagCrossRef("a", "kotlin"),
            TweetTagCrossRef("a", "android"),
            TweetTagCrossRef("b", "compose"),
        )

        val result = repository.getTagsForItems(listOf("a", "b", "c"))

        assertEquals(listOf("kotlin", "android"), result["a"])
        assertEquals(listOf("compose"), result["b"])
        // c had no rows → explicit empty entry so the ViewModel overwrite clears its chips.
        assertEquals(emptyList<String>(), result["c"])
    }

    @Test
    fun getTagsForItems_emptyInput_returnsEmptyMapWithoutQuerying() = runTest(dispatcher) {
        val result = repository.getTagsForItems(emptyList())
        assertTrue(result.isEmpty())
    }
}
