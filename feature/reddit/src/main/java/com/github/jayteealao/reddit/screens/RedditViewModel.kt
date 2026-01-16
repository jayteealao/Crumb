package com.github.jayteealao.reddit.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.github.jayteealao.reddit.data.RedditPrefs
import com.github.jayteealao.reddit.data.RedditRepository
import com.github.jayteealao.reddit.models.RedditPostData
import com.github.jayteealao.reddit.services.RedditApiService
import com.github.jayteealao.reddit.services.RedditAuthClient
import com.skydoves.sandwich.message
import com.skydoves.sandwich.onError
import com.skydoves.sandwich.onSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for Reddit authentication and bookmarks
 */
@HiltViewModel
class RedditViewModel @Inject constructor(
    private val redditRepository: RedditRepository,
    private val redditAuthClient: RedditAuthClient,
    private val redditApiService: RedditApiService,
    private val redditPrefs: RedditPrefs
) : ViewModel() {

    private val _isAccessTokenAvailable = MutableStateFlow(false)
    val isAccessTokenAvailable: StateFlow<Boolean> = _isAccessTokenAvailable

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username

    init {
        checkAccessToken()
        redditRepository.buildDatabase()
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
    fun pagingFlowData(): Flow<PagingData<RedditPostData>> = redditRepository.getPagingPosts()

    /**
     * Build/refresh database
     */
    fun buildDatabase() = redditRepository.buildDatabase()

    /**
     * Search posts
     */
    fun searchPosts(query: String): Flow<PagingData<RedditPostData>> = redditRepository.searchPosts(query)
}
