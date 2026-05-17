package com.github.jayteealao.crumbs.screens

import android.content.Intent
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import com.github.jayteealao.crumbs.data.BannerState
import com.github.jayteealao.crumbs.data.BookmarkSource
import com.github.jayteealao.crumbs.data.DeletedBookmarkRepository
import com.github.jayteealao.crumbs.data.SnackbarEvent
import com.github.jayteealao.crumbs.data.SyncErrorBus
import com.github.jayteealao.crumbs.data.SyncErrorEvent
import com.github.jayteealao.crumbs.designsystem.components.BottomNavTab
import com.github.jayteealao.reddit.screens.RedditBookmarksRoute
import com.github.jayteealao.reddit.screens.RedditViewModel
import com.github.jayteealao.twitter.screens.BookmarksViewModel
import com.github.jayteealao.twitter.screens.LoginViewModel
import com.github.jayteealao.twitter.screens.TwitterBookmarksRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Holder Hilt-provided services for HomeRoute. Lives at the route scope so that
 * tab-switching does not tear down event collectors.
 */
@HiltViewModel
class HomeServicesViewModel @Inject constructor(
    val syncErrorBus: SyncErrorBus,
    val tombstoneRepository: DeletedBookmarkRepository,
) : ViewModel()

@Composable
fun HomeRoute(
    navController: NavController,
    twitterAuthCode: String = "",
    loginViewModel: LoginViewModel = hiltViewModel(),
    redditViewModel: RedditViewModel = hiltViewModel(),
    bookmarksViewModel: BookmarksViewModel = hiltViewModel(),
    services: HomeServicesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(BottomNavTab.TWITTER) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var twitterBanner by remember { mutableStateOf<BannerState?>(null) }
    var redditBanner by remember { mutableStateOf<BannerState?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    val twitterFilter by bookmarksViewModel.filter.collectAsState()
    val redditFilter by redditViewModel.filter.collectAsState()

    val activeFilter = when (selectedTab) {
        BottomNavTab.TWITTER -> twitterFilter
        BottomNavTab.REDDIT -> redditFilter
        BottomNavTab.ALL -> twitterFilter
        BottomNavTab.MAP -> twitterFilter
    }
    val activeBanner = when (selectedTab) {
        BottomNavTab.TWITTER -> twitterBanner
        BottomNavTab.REDDIT -> redditBanner
        else -> null
    }

    LaunchedEffect(Unit) {
        services.syncErrorBus.events.collect { event ->
            when (event) {
                is SyncErrorEvent.TwitterAuth401 -> {
                    twitterBanner = BannerState(
                        source = BookmarkSource.TWITTER,
                        kicker = "ERR · RECONNECT TWITTER",
                        detail = "Twitter session expired. Tap to reconnect.",
                        ctaLabel = "RECONNECT",
                    )
                }
                is SyncErrorEvent.RedditAuth401 -> {
                    redditBanner = BannerState(
                        source = BookmarkSource.REDDIT,
                        kicker = "ERR · RECONNECT REDDIT",
                        detail = "Reddit session expired. Tap to reconnect.",
                        ctaLabel = "RECONNECT",
                    )
                }
                is SyncErrorEvent.Other -> Unit
            }
        }
    }

    LaunchedEffect(Unit) {
        services.tombstoneRepository.events.collect { event ->
            when (event) {
                is SnackbarEvent.UndoableDelete -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "DELETED",
                        actionLabel = "UNDO",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        when (event.source) {
                            BookmarkSource.TWITTER -> bookmarksViewModel.undoDelete(event.id)
                            BookmarkSource.REDDIT -> redditViewModel.undoDelete(event.id)
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    HomeScreen(
        uiState = HomeUiState(
            selectedTab = selectedTab,
            isSearchActive = isSearchActive,
            searchQuery = searchQuery,
            selectedFilterChipIds = setOf(activeFilter.type.name.lowercase()),
            bannerState = activeBanner,
        ),
        onTabSelected = { selectedTab = it },
        onSearchQueryChange = { searchQuery = it },
        onSearchActiveChange = { isSearchActive = it },
        onChipToggled = { id ->
            when (selectedTab) {
                BottomNavTab.TWITTER, BottomNavTab.ALL, BottomNavTab.MAP -> bookmarksViewModel.onTypeChipToggled(id)
                BottomNavTab.REDDIT -> redditViewModel.onTypeChipToggled(id)
            }
        },
        onSortClick = { /* sort dialog deferred to a follow-up slice */ },
        onBannerCta = {
            when (activeBanner?.source) {
                BookmarkSource.TWITTER -> context.startActivity(loginViewModel.authIntent())
                BookmarkSource.REDDIT -> context.startActivity(redditViewModel.authIntent())
                else -> Unit
            }
        },
        snackbarHostState = snackbarHostState,
    ) { tab, padding ->
        when (tab) {
            BottomNavTab.TWITTER -> TwitterBookmarksRoute(
                navController = navController,
                contentPadding = padding,
                twitterAuthCode = twitterAuthCode,
                bookmarksViewModel = bookmarksViewModel,
                loginViewModel = loginViewModel,
            )
            BottomNavTab.REDDIT -> RedditBookmarksRoute(
                navController = navController,
                contentPadding = padding,
                bookmarksViewModel = bookmarksViewModel,
                redditViewModel = redditViewModel,
            )
            BottomNavTab.ALL -> AllBookmarksRoute(
                contentPadding = padding,
                loginViewModel = loginViewModel,
                bookmarksViewModel = bookmarksViewModel,
                redditViewModel = redditViewModel,
            )
            BottomNavTab.MAP -> MapViewRoute(contentPadding = padding)
        }
    }
}
