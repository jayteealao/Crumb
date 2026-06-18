package com.github.jayteealao.crumbs.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.twitter.data.TweetDao
import com.github.jayteealao.twitter.models.TweetData
import com.github.jayteealao.twitter.screens.toBookmark
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the ThreadDetail surface. The route passes a `bookmarkId` via the
 * nav-graph SavedStateHandle. The VM resolves it to the root [TweetData]
 * (carrying `conversation_id`) and observes every other locally-bookmarked
 * tweet in the same conversation as a hot flow so newly-bookmarked replies
 * appear without re-navigation.
 *
 * Bookmarks whose `conversation_id` is empty collapse to an only-root state
 * (no error). Bookmarks that cannot be resolved at all surface as an error
 * state for the screen to render.
 */
@HiltViewModel
class ThreadDetailViewModel @Inject constructor(
    private val tweetDao: TweetDao,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val bookmarkId: String? = savedStateHandle["bookmarkId"]

    private val rootSeed = MutableStateFlow<RootResolution>(RootResolution.Pending)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ThreadDetailUiState> = rootSeed
        .flatMapLatest { resolution ->
            when (resolution) {
                RootResolution.Pending -> flowOf(ThreadDetailUiState.Loading)
                RootResolution.NotFound -> flowOf(
                    ThreadDetailUiState.Error("Bookmark not found"),
                )
                is RootResolution.Found -> {
                    val rootBookmark = resolution.data.toBookmark()
                    val convoId = resolution.data.tweet.conversationId
                    if (convoId.isBlank()) {
                        flowOf(
                            ThreadDetailUiState.Loaded(
                                root = rootBookmark,
                                replies = persistentListOf(),
                            ),
                        )
                    } else {
                        tweetDao.tweetsByConversationId(convoId).map { rows ->
                            val replies = rows
                                .asSequence()
                                .filter { it.tweet.id != rootBookmark.id }
                                .map { it.toBookmark() }
                                .toList()
                                .toImmutableList()
                            ThreadDetailUiState.Loaded(
                                root = rootBookmark,
                                replies = replies,
                            )
                        }
                    }
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ThreadDetailUiState.Loading,
        )

    init {
        viewModelScope.launch {
            val id = bookmarkId
            rootSeed.value = when {
                id.isNullOrEmpty() -> RootResolution.NotFound
                else -> tweetDao.getTweetById(id)?.let { RootResolution.Found(it) }
                    ?: RootResolution.NotFound
            }
        }
    }

    private sealed interface RootResolution {
        data object Pending : RootResolution
        data object NotFound : RootResolution
        data class Found(val data: TweetData) : RootResolution
    }
}

sealed interface ThreadDetailUiState {
    data object Loading : ThreadDetailUiState
    data class Error(val message: String) : ThreadDetailUiState
    data class Loaded(
        val root: Bookmark,
        val replies: ImmutableList<Bookmark>,
    ) : ThreadDetailUiState
}
