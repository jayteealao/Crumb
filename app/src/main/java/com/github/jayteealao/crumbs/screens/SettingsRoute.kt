package com.github.jayteealao.crumbs.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.github.jayteealao.crumbs.Screens
import com.github.jayteealao.twitter.screens.BookmarksViewModel
import com.github.jayteealao.twitter.screens.DisconnectEvent

/**
 * Navigation entry point for the settings destination. Observes sync status from
 * [BookmarksViewModel] and handles the disconnect flow, navigating to the ConnectX screen
 * after a successful disconnect event.
 *
 * @param navController Used to navigate to ConnectX and clear the settings back-stack entry after disconnect.
 * @param bookmarksViewModel Provides sync status and the disconnect action for the X account.
 */
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
