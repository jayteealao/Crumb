package com.github.jayteealao.crumbs.screens

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.github.jayteealao.twitter.screens.BookmarksViewModel

@Composable
fun SettingsRoute(
    navController: NavHostController,
    bookmarksViewModel: BookmarksViewModel,
) {
    val context = LocalContext.current
    val syncStatus by bookmarksViewModel.syncStatus.collectAsStateWithLifecycle()

    SettingsScreen(
        syncStatus = syncStatus,
        onDisconnectClick = {
            Toast.makeText(
                context,
                "Disconnect coming with cutover",
                Toast.LENGTH_SHORT,
            ).show()
        },
    )
}
