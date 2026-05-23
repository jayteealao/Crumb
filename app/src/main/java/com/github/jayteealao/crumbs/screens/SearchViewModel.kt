package com.github.jayteealao.crumbs.screens

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jayteealao.crumbs.data.SearchRepository
import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.pref.addRecentSearch
import com.github.jayteealao.pref.recentSearches
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * SearchScreen state machine. The query flow is debounced 300 ms (NowInAndroid
 * default, matches Compose docs' search-bar recipe) and switched into the
 * repository via `flatMapLatest` so an in-flight FTS query is cancelled when
 * the user keeps typing. Blank queries short-circuit to [SearchUiState.Idle]
 * so the recent-searches view renders without a needless DAO round-trip.
 *
 * Recent searches read from Preferences DataStore directly — promoted to
 * [SearchUiState.Idle]'s `recent` field so a single state observation drives
 * both empty-state composition modes.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val searchRepository: SearchRepository,
    @Suppress("unused") savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")

    val query: StateFlow<String> = queryFlow

    val recentSearches: StateFlow<ImmutableList<String>> = context
        .recentSearches()
        .map { it.toImmutableList() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            persistentListOf(),
        )

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SearchUiState> = queryFlow
        .debounce(DEBOUNCE_MS)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.isBlank()) {
                flowOf(SearchUiState.Idle)
            } else {
                searchRepository.search(q)
                    .map { hits ->
                        if (hits.isEmpty()) {
                            SearchUiState.Empty(q)
                        } else {
                            SearchUiState.Results(q, hits.toImmutableList())
                        }
                    }
                    .onStart { emit(SearchUiState.Loading(q)) }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SearchUiState.Idle,
        )

    fun onQueryChanged(q: String) {
        queryFlow.value = q
    }

    /**
     * Promote the current query to the recent-searches list. Called when the
     * IME Search action fires or a hit is opened — i.e. when the user signals
     * the query is "done", not on every keystroke.
     */
    fun onSearchSubmitted() {
        val q = queryFlow.value.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            context.addRecentSearch(q)
        }
    }

    fun onRecentSearchSelected(query: String) {
        queryFlow.value = query
    }

    companion object {
        const val DEBOUNCE_MS = 300L
    }
}

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data class Loading(val query: String) : SearchUiState
    data class Empty(val query: String) : SearchUiState
    data class Results(
        val query: String,
        val hits: ImmutableList<Bookmark>,
    ) : SearchUiState
}
