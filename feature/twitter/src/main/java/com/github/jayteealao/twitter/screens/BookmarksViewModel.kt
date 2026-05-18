package com.github.jayteealao.twitter.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.github.jayteealao.crumbs.data.FilterState
import com.github.jayteealao.crumbs.data.TypeFilter
import com.github.jayteealao.twitter.data.Repository
import com.github.jayteealao.twitter.models.TweetData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: Repository
) : ViewModel() {

    init {
        repository.buildDatabase()
    }

    val isRefreshing: StateFlow<Boolean> = repository.isRefreshing

    private val _filter = MutableStateFlow(FilterState())
    val filter: StateFlow<FilterState> = _filter.asStateFlow()

    val pagingFlow: Flow<PagingData<TweetData>> = _filter
        .flatMapLatest { state -> repository.pagingTweetData(state) }
        .cachedIn(viewModelScope)

    fun pagingFlowData(order: String = "default"): Flow<PagingData<TweetData>> = pagingFlow

    fun buildDatabase() = repository.buildDatabase()

    fun refresh() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.refreshBookmarks()
            }
        }
    }

    fun onTypeChipToggled(typeId: String) {
        val next = runCatching { TypeFilter.valueOf(typeId.uppercase()) }.getOrDefault(TypeFilter.ALL)
        _filter.update { it.copy(type = next) }
    }

    fun onTagToggled(tag: String) {
        _filter.update {
            val tags = if (tag in it.selectedTags) it.selectedTags - tag else it.selectedTags + tag
            it.copy(selectedTags = tags.toPersistentSet())
        }
    }

    fun onTagsApplied(tags: Set<String>) {
        _filter.update { it.copy(selectedTags = tags.toPersistentSet()) }
    }

    fun clearTagFilter() {
        _filter.update { it.copy(selectedTags = persistentSetOf()) }
    }

    fun softDelete(id: String) {
        viewModelScope.launch { repository.softDelete(id) }
    }

    fun undoDelete(id: String) {
        viewModelScope.launch { repository.undoDelete(id) }
    }

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

    fun loadTagsForItems(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val batch = repository.getTagsForItems(ids)
            // Merge: preserve existing entries not in the batch so single-item
            // updates from saveTags() are not overwritten.
            _tagsForTweet.value = _tagsForTweet.value + batch
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

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }
}
