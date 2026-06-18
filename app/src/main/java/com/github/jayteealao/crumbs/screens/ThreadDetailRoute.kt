package com.github.jayteealao.crumbs.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController

/**
 * Navigation entry point for the thread-detail destination. Resolves [ThreadDetailViewModel]
 * from the Hilt graph and forwards its state into [ThreadDetailScreen]. Root-card taps open
 * the canonical source URL via Intent.ACTION_VIEW — the thread surface is read-only.
 *
 * @param navController Used to pop back to the search or feed when the user closes the thread.
 * @param viewModel Loads the root tweet and its replies and exposes them as [ThreadDetailUiState].
 */
@Composable
fun ThreadDetailRoute(
    navController: NavController,
    viewModel: ThreadDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ThreadDetailScreen(
        uiState = uiState,
        onClose = { navController.popBackStack() },
        onRootCardClick = { bookmark ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(bookmark.sourceUrl))
            runCatching { context.startActivity(intent) }
        },
    )
}
