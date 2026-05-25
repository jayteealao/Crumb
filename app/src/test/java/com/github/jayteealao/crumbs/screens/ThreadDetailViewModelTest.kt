package com.github.jayteealao.crumbs.screens

import androidx.lifecycle.SavedStateHandle
import com.github.jayteealao.twitter.data.TweetDao
import com.github.jayteealao.twitter.models.TweetEntity
import com.github.jayteealao.twitter.models.TweetData
import com.github.jayteealao.twitter.models.TwitterUserEntity
import com.github.jayteealao.twitter.models.TweetPublicMetrics
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
 * Unit tests for [ThreadDetailViewModel] covering:
 *
 * 1. null bookmarkId  → Error("Bookmark not found")
 * 2. bookmarkId not found in DAO → Error("Bookmark not found")
 * 3. Bookmark with blank conversationId → Loaded(root, emptyList())
 * 4. Bookmark with non-blank conversationId → Loaded(root, replies excluding root)
 * 5. Hot Flow: new reply added to conversation → flatMapLatest uiState updates
 *
 * Uses [StandardTestDispatcher] + [advanceUntilIdle] for deterministic coroutine
 * control. [TweetDao] is mocked with mockk; suspend functions use [coEvery].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThreadDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var tweetDao: TweetDao

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        tweetDao = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun tweetEntity(
        id: String,
        conversationId: String = "",
        authorId: String = "user1",
    ) = TweetEntity(
        id = id,
        text = "Tweet text for $id",
        createdAt = "2024-01-01T00:00:00.000Z",
        authorId = authorId,
        conversationId = conversationId,
        inReplyToUserId = null,
        lang = "en",
        referenced = false,
        order = 0,
        pendingDelete = false,
    )

    private fun userEntity(id: String = "user1") = TwitterUserEntity(
        id = id,
        name = "Test User",
        username = "testuser",
        profileImageUrl = null,
        verified = false,
        verifiedType = null,
        description = null,
        mentionedIn = null,
    )

    private fun tweetData(
        id: String,
        conversationId: String = "",
        authorId: String = "user1",
    ) = TweetData(
        tweet = tweetEntity(id, conversationId, authorId),
        user = userEntity(authorId),
        publicMetrics = TweetPublicMetrics(
            retweetCount = 0,
            replyCount = 0,
            likeCount = 0,
            quoteCount = 0,
            viewCount = 0,
            tweetId = id,
        ),
        media = emptyList(),
        includes = emptyList(),
        tweetTextAnnotation = emptyList(),
    )

    private fun makeVm(bookmarkId: String?): ThreadDetailViewModel {
        val handle = if (bookmarkId != null) {
            SavedStateHandle(mapOf("bookmarkId" to bookmarkId))
        } else {
            SavedStateHandle()
        }
        return ThreadDetailViewModel(tweetDao, handle)
    }

    /** Subscribe to uiState so WhileSubscribed activates upstream collection. */
    private fun ThreadDetailViewModel.activateUiState() =
        uiState.onEach {}.launchIn(kotlinx.coroutines.CoroutineScope(dispatcher))

    // -----------------------------------------------------------------------
    // Test 1: null bookmarkId → Error state
    // -----------------------------------------------------------------------

    @Test
    fun nullBookmarkId_emitsErrorState() = runTest(dispatcher) {
        coEvery { tweetDao.getTweetById(any()) } returns null
        // tweetsByConversationId should not be called; stub it anyway to be safe
        every { tweetDao.tweetsByConversationId(any()) } returns MutableStateFlow(emptyList())

        val vm = makeVm(bookmarkId = null)
        val collector = vm.activateUiState()

        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Error, got $state", state is ThreadDetailUiState.Error)
        val error = state as ThreadDetailUiState.Error
        assertTrue(
            "Error message should mention 'not found', got: ${error.message}",
            error.message.contains("not found", ignoreCase = true),
        )

        collector.cancel()
    }

    // -----------------------------------------------------------------------
    // Test 2: bookmarkId not found in DAO → Error state
    // -----------------------------------------------------------------------

    @Test
    fun bookmarkIdNotInDao_emitsErrorState() = runTest(dispatcher) {
        coEvery { tweetDao.getTweetById("missing-id") } returns null
        every { tweetDao.tweetsByConversationId(any()) } returns MutableStateFlow(emptyList())

        val vm = makeVm(bookmarkId = "missing-id")
        val collector = vm.activateUiState()

        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Error for missing bookmarkId, got $state", state is ThreadDetailUiState.Error)
        assertEquals(
            "Bookmark not found",
            (state as ThreadDetailUiState.Error).message,
        )

        collector.cancel()
    }

    // -----------------------------------------------------------------------
    // Test 3: Found bookmark with blank conversationId → Loaded(root, emptyList())
    // -----------------------------------------------------------------------

    @Test
    fun blankConversationId_emitsLoadedWithEmptyReplies() = runTest(dispatcher) {
        val data = tweetData(id = "root-id", conversationId = "")
        coEvery { tweetDao.getTweetById("root-id") } returns data
        every { tweetDao.tweetsByConversationId(any()) } returns MutableStateFlow(emptyList())

        val vm = makeVm(bookmarkId = "root-id")
        val collector = vm.activateUiState()

        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Loaded, got $state", state is ThreadDetailUiState.Loaded)
        val loaded = state as ThreadDetailUiState.Loaded
        assertEquals("root-id", loaded.root.id)
        assertTrue("Expected empty replies for blank conversationId", loaded.replies.isEmpty())

        collector.cancel()
    }

    // -----------------------------------------------------------------------
    // Test 4: Found bookmark with non-blank conversationId → replies exclude root
    // -----------------------------------------------------------------------

    @Test
    fun nonBlankConversationId_emitsLoadedWithRepliesExcludingRoot() = runTest(dispatcher) {
        val convoId = "convo-123"
        val rootData = tweetData(id = "root-id", conversationId = convoId)
        val reply1Data = tweetData(id = "reply-1", conversationId = convoId)
        val reply2Data = tweetData(id = "reply-2", conversationId = convoId)
        val allInConvo = listOf(rootData, reply1Data, reply2Data)

        coEvery { tweetDao.getTweetById("root-id") } returns rootData
        every { tweetDao.tweetsByConversationId(convoId) } returns MutableStateFlow(allInConvo)

        val vm = makeVm(bookmarkId = "root-id")
        val collector = vm.activateUiState()

        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Loaded, got $state", state is ThreadDetailUiState.Loaded)
        val loaded = state as ThreadDetailUiState.Loaded
        assertEquals("root-id", loaded.root.id)
        assertEquals("Expected 2 replies (root excluded)", 2, loaded.replies.size)
        assertTrue(
            "Replies must not contain root bookmark id",
            loaded.replies.none { it.id == "root-id" },
        )
        assertEquals("reply-1", loaded.replies[0].id)
        assertEquals("reply-2", loaded.replies[1].id)

        collector.cancel()
    }

    // -----------------------------------------------------------------------
    // Test 5: Hot Flow — new reply added to conversation updates uiState
    // -----------------------------------------------------------------------

    @Test
    fun newReplyAddedToConversation_updatesUiStateViaFlatMapLatest() = runTest(dispatcher) {
        val convoId = "convo-hot"
        val rootData = tweetData(id = "root-hot", conversationId = convoId)
        val reply1Data = tweetData(id = "reply-hot-1", conversationId = convoId)
        val reply2Data = tweetData(id = "reply-hot-2", conversationId = convoId)

        // A MutableStateFlow acting as the live conversation query result
        val convoFlow = MutableStateFlow<List<TweetData>>(listOf(rootData))

        coEvery { tweetDao.getTweetById("root-hot") } returns rootData
        every { tweetDao.tweetsByConversationId(convoId) } returns convoFlow

        val vm = makeVm(bookmarkId = "root-hot")
        val collector = vm.activateUiState()

        advanceUntilIdle()

        // Verify initial state: root only in conversation → 0 replies
        val initialState = vm.uiState.value
        assertTrue("Expected initial Loaded state, got $initialState", initialState is ThreadDetailUiState.Loaded)
        assertTrue(
            "Expected 0 replies initially",
            (initialState as ThreadDetailUiState.Loaded).replies.isEmpty(),
        )

        // Push a new reply into the hot flow
        convoFlow.value = listOf(rootData, reply1Data)
        advanceUntilIdle()

        val afterFirstReply = vm.uiState.value
        assertTrue("Expected Loaded after first reply, got $afterFirstReply", afterFirstReply is ThreadDetailUiState.Loaded)
        assertEquals(
            "Expected 1 reply after first push",
            1,
            (afterFirstReply as ThreadDetailUiState.Loaded).replies.size,
        )
        assertEquals("reply-hot-1", afterFirstReply.replies[0].id)

        // Push a second reply
        convoFlow.value = listOf(rootData, reply1Data, reply2Data)
        advanceUntilIdle()

        val afterSecondReply = vm.uiState.value
        assertTrue("Expected Loaded after second reply, got $afterSecondReply", afterSecondReply is ThreadDetailUiState.Loaded)
        assertEquals(
            "Expected 2 replies after second push",
            2,
            (afterSecondReply as ThreadDetailUiState.Loaded).replies.size,
        )

        collector.cancel()
    }
}
