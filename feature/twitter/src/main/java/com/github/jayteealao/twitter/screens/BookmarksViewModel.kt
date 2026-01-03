package com.github.jayteealao.twitter.screens

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.paging.PagingData
import com.github.jayteealao.twitter.data.IdForThread
import com.github.jayteealao.twitter.data.Repository
import com.github.jayteealao.twitter.models.TweetData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
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

}
