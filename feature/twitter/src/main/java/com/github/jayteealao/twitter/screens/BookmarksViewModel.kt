package com.github.jayteealao.twitter.screens

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.github.jayteealao.twitter.data.IdForThread
import com.github.jayteealao.twitter.data.Repository
import com.github.jayteealao.twitter.models.TweetData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: Repository
) : ViewModel() {

    init {
        repository.buildDatabase()
    }

//    val refreshed = checkNotNull(savedStateHandle.get<Boolean>("refreshed"))

    fun pagingFlowData(order: String = "default"): Flow<PagingData<TweetData>> = sortOrder[order]!!()

    fun buildDatabase() = repository.buildDatabase()

    fun saveThreads(tweetAuthorId: String, conversationId: String) = repository.saveTweetThreads(tweetAuthorId, conversationId)

    fun saveThreadsAppOnly(tweetAuthorId: String, conversationId: String) = repository.saveTweetThreadsAppOnly(tweetAuthorId, conversationId)

    fun getThreadIds(): List<IdForThread> = repository.getThreadIds()
    private val sortOrder = mutableStateMapOf(
        "default" to { repository.pagingTweetData() }
    )

    // Tag operations
    private val _tagsForTweet = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val tagsForTweet: StateFlow<Map<String, List<String>>> = _tagsForTweet

    private val _allTags = MutableStateFlow<List<String>>(emptyList())
    val allTags: StateFlow<List<String>> = _allTags

    init {
        loadAllTags()
    }

    fun loadTagsForTweet(tweetId: String) {
        viewModelScope.launch {
            val tags = repository.getTagsForTweet(tweetId)
            _tagsForTweet.value = _tagsForTweet.value + (tweetId to tags)
        }
    }

    fun loadAllTags() {
        viewModelScope.launch {
            _allTags.value = repository.getAllTags()
        }
    }

    fun saveTags(tweetId: String, tags: List<String>) {
        viewModelScope.launch {
            repository.saveTags(tweetId, tags)
            loadTagsForTweet(tweetId)
            loadAllTags()
        }
    }

}
