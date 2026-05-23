package com.github.jayteealao.crumbs.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.github.jayteealao.crumbs.Screens

/**
 * Thin wrapper that hoists [SearchViewModel] state into [SearchScreen] and
 * routes user actions back to the VM or the navigation graph. Bookmark taps
 * navigate to ThreadDetail when the bookmark is a Twitter thread; otherwise
 * the canonical source URL opens through Intent.ACTION_VIEW (same handoff
 * shape used by the feed screens — no Custom Tabs indirection so the install
 * stays consistent).
 */
@Composable
fun SearchRoute(
    navController: NavController,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val query by viewModel.query.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recent by viewModel.recentSearches.collectAsStateWithLifecycle()

    SearchScreen(
        query = query,
        uiState = uiState,
        recentSearches = recent,
        onQueryChange = viewModel::onQueryChanged,
        onSubmit = viewModel::onSearchSubmitted,
        onBack = { navController.popBackStack() },
        onRecentSelected = viewModel::onRecentSearchSelected,
        onBookmarkClick = { bookmark ->
            viewModel.onSearchSubmitted()
            if (bookmark.isThread) {
                navController.navigate("${Screens.THREADDETAIL.name}?bookmarkId=${bookmark.id}")
            } else {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(bookmark.sourceUrl))
                runCatching { context.startActivity(intent) }
            }
        },
    )
}
