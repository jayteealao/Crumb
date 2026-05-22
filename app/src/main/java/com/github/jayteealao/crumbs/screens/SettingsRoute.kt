package com.github.jayteealao.crumbs.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.github.jayteealao.crumbs.Screens
import com.github.jayteealao.twitter.screens.BookmarksViewModel
import com.github.jayteealao.twitter.screens.DisconnectEvent

@Composable
fun SettingsRoute(
    navController: NavHostController,
    bookmarksViewModel: BookmarksViewModel,
) {
    val syncStatus by bookmarksViewModel.syncStatus.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        bookmarksViewModel.disconnectEvents.collect { event ->
            if (event is DisconnectEvent.Success) {
                navController.navigate(Screens.CONNECTX.name) {
                    popUpTo(Screens.SETTINGS.name) { inclusive = true }
                }
            }
        }
    }

    SettingsScreen(
        syncStatus = syncStatus,
        onDisconnectClick = { bookmarksViewModel.disconnectX() },
    )
}
