package com.github.jayteealao.reddit.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.github.jayteealao.crumbs.data.FilterState
import com.github.jayteealao.crumbs.data.TagRepository
import com.github.jayteealao.crumbs.data.TypeFilter
import com.github.jayteealao.reddit.data.RedditPrefs
import com.github.jayteealao.reddit.data.RedditRepository
import com.github.jayteealao.reddit.models.RedditPostData
import com.github.jayteealao.reddit.services.RedditApiService
import com.github.jayteealao.reddit.services.RedditAuthClient
import com.skydoves.sandwich.message
import com.skydoves.sandwich.onError
import com.skydoves.sandwich.onSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for Reddit authentication and bookmarks
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RedditViewModel @Inject constructor(
    private val redditRepository: RedditRepository,
    private val redditAuthClient: RedditAuthClient,
    private val redditApiService: RedditApiService,
    private val redditPrefs: RedditPrefs,
    private val tagRepository: TagRepository
) : ViewModel() {

    private val _isAccessTokenAvailable = MutableStateFlow(false)
    val isAccessTokenAvailable: StateFlow<Boolean> = _isAccessTokenAvailable

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username

    private val _filter = MutableStateFlow(FilterState())
    val filter: StateFlow<FilterState> = _filter.asStateFlow()

    val pagingFlow: Flow<PagingData<RedditPostData>> = _filter
        .flatMapLatest { state -> redditRepository.pagingPostsData(state) }
        .cachedIn(viewModelScope)

    private val _tagsForTweet = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val tagsForTweet: StateFlow<Map<String, List<String>>> = _tagsForTweet

    private val _allTags = MutableStateFlow<List<String>>(emptyList())
    val allTags: StateFlow<List<String>> = _allTags

    init {
        checkAccessToken()
        redditRepository.buildDatabase()
        loadAllTags()
    }

    /**
     * Check if we have a valid access token
     */
    private fun checkAccessToken() {
        viewModelScope.launch {
            val accessToken = redditPrefs.accessToken.first()
            _isAccessTokenAvailable.value = accessToken.isNotBlank()

            if (_isAccessTokenAvailable.value) {
                fetchUsername()
            }
        }
    }

    /**
     * Get auth intent for Reddit OAuth
     */
    fun authIntent() = redditAuthClient.getAuthIntent()

    /**
     * Exchange authorization code for access token
     */
    fun getAccessToken(code: String) {
        viewModelScope.launch {
            val token = redditAuthClient.getAccessToken(code)
            if (token != null) {
                _isAccessTokenAvailable.value = true
                fetchUsername()
                redditRepository.buildDatabase()
            }
        }
    }

    /**
     * Fetch current user's username
     */
    private fun fetchUsername() {
        viewModelScope.launch {
            val accessToken = redditPrefs.accessToken.first()

            if (accessToken.isNotBlank()) {
                val result = redditApiService.getUser("Bearer $accessToken")
                result.onSuccess {
                    Timber.d("Reddit user: ${data.name}")
                    _username.value = data.name
                    viewModelScope.launch {
                        redditPrefs.saveUsername(data.name)
                    }
                }.onError {
                    Timber.e("Failed to get Reddit user: ${message()}")
                }
            }
        }
    }

    /**
     * Get paging flow for Reddit posts
     */
    fun pagingFlowData(): Flow<PagingData<RedditPostData>> = pagingFlow

    /**
     * Build/refresh database
     */
    fun buildDatabase() = redditRepository.buildDatabase()

    /**
     * Search posts
     */
    fun searchPosts(query: String): Flow<PagingData<RedditPostData>> = redditRepository.searchPosts(query)

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
        viewModelScope.launch { redditRepository.softDelete(id) }
    }

    fun undoDelete(id: String) {
        viewModelScope.launch { redditRepository.undoDelete(id) }
    }

    fun loadTagsForTweet(id: String) {
        viewModelScope.launch {
            val tags = tagRepository.getTagsForTweet(id)
            _tagsForTweet.value = _tagsForTweet.value + (id to tags)
        }
    }

    fun loadAllTags() {
        viewModelScope.launch {
            _allTags.value = tagRepository.getAllTags()
        }
    }

    fun saveTags(id: String, tags: List<String>) {
        viewModelScope.launch {
            tagRepository.saveTags(id, tags)
            loadTagsForTweet(id)
            loadAllTags()
        }
    }

    fun logout() {
        viewModelScope.launch {
            redditRepository.logout()
            _isAccessTokenAvailable.value = false
            _username.value = ""
        }
    }
}
