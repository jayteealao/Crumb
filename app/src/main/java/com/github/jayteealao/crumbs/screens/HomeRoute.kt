package com.github.jayteealao.crumbs.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.github.jayteealao.crumbs.data.BannerState
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.data.SnackbarBus
import com.github.jayteealao.crumbs.data.SnackbarEvent
import com.github.jayteealao.crumbs.data.SyncErrorBus
import com.github.jayteealao.crumbs.data.SyncErrorEvent
import com.github.jayteealao.crumbs.designsystem.components.BottomNavTab
import com.github.jayteealao.reddit.screens.RedditBookmarksRoute
import com.github.jayteealao.reddit.screens.RedditViewModel
import com.github.jayteealao.crumbs.Screens
import com.github.jayteealao.twitter.data.TwitterSnackbarEvent
import com.github.jayteealao.twitter.screens.BookmarksViewModel
import com.github.jayteealao.twitter.screens.LoginViewModel
import com.github.jayteealao.twitter.screens.TwitterBookmarksRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Holder Hilt-provided services for HomeRoute. Lives at the route scope so that
 * tab-switching does not tear down event collectors.
 */
@HiltViewModel
class HomeServicesViewModel @Inject constructor(
    val syncErrorBus: SyncErrorBus,
    val snackbarBus: SnackbarBus,
) : ViewModel()

/**
 * Navigation entry point for the home destination. Wires Hilt ViewModels to [HomeScreen] and
 * coordinates cross-tab concerns such as reconnect banners, snackbar feedback, and filter state.
 *
 * @param navController Used to navigate to Search, ConnectX, and OAuth redirect destinations.
 * @param twitterAuthCode OAuth authorization code forwarded from the deep-link intent, if present.
 * @param loginViewModel Provides Twitter access-token availability and user info.
 * @param redditViewModel Provides Reddit access-token availability, filter state, and auth intent.
 * @param bookmarksViewModel Provides Twitter bookmark paging, filter state, and sync status.
 * @param services Hilt-scoped holder for [SyncErrorBus] and [SnackbarBus] whose lifetimes must
 *   outlive individual tab recompositions.
 */
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
    val snackbarScope = rememberCoroutineScope()

    // collectAsStateWithLifecycle stops collecting when the route goes off-
    // screen (e.g. settings deep-link) so background flows don't keep waking
    // the route just to drop emissions on the floor.
    val twitterFilter by bookmarksViewModel.filter.collectAsStateWithLifecycle()
    val redditFilter by redditViewModel.filter.collectAsStateWithLifecycle()
    val twitterAccess by loginViewModel.isAccessTokenAvailable.collectAsStateWithLifecycle()
    val redditAccess by redditViewModel.isAccessTokenAvailable.collectAsStateWithLifecycle()
    val syncStatus by bookmarksViewModel.syncStatus.collectAsStateWithLifecycle()
    // Live Twitter feed count for the SAVED header; tracks the active tag/type filter.
    val twitterCount by bookmarksViewModel.itemCount.collectAsStateWithLifecycle()

    // Reconnect banner reflects sync_status.linked from the server doc; takes
    // precedence over the legacy 401 banner because the new server-driven
    // flow makes the device's access-token state irrelevant.
    LaunchedEffect(syncStatus?.linked) {
        val linked = syncStatus?.linked
        if (linked == false) {
            twitterBanner = BannerState(
                source = BookmarkSource.Twitter,
                kicker = "RECONNECT X",
                detail = "Your X connection needs renewing",
                ctaLabel = "RECONNECT",
            )
        } else if (linked == true) {
            twitterBanner = null
        }
    }

    // Surface triggerPoll feedback (debounce / in-progress) as snackbars on
    // the home host so the user sees pull-to-refresh outcomes. collectLatest (not
    // collect) cancels an in-flight showSnackbar when a newer event arrives, so a
    // superseded transient "Fetching…" is dropped instead of queuing behind a
    // long-lived snackbar (e.g. "BOOKMARK DELETED").
    LaunchedEffect(Unit) {
        bookmarksViewModel.snackbarEvents.collectLatest { event ->
            val message = when (event) {
                is TwitterSnackbarEvent.Debounced -> {
                    val secs = event.retryAfterSeconds ?: 60
                    "FETCH PAUSED. TRY AGAIN IN $secs SECONDS."
                }
                is TwitterSnackbarEvent.InProgress -> "Fetching your bookmarks..."
                is TwitterSnackbarEvent.GenericFailure -> "Couldn't fetch bookmarks. Please try again."
            }
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short,
            )
        }
    }

    LaunchedEffect(twitterAccess) {
        if (twitterAccess) {
            twitterBanner = null
            // Drop the replay slot so a warm-start subscription does not
            // resurrect the stale auth event the next time the route mounts.
            services.syncErrorBus.clear()
        }
    }
    LaunchedEffect(redditAccess) {
        if (redditAccess) {
            redditBanner = null
            services.syncErrorBus.clear()
        }
    }

    // derivedStateOf collapses transitive recompositions: callers reading
    // activeFilter/activeBanner only invalidate when the *resolved* value
    // changes, not on every twitter/reddit emission.
    val activeFilter by remember {
        derivedStateOf {
            when (selectedTab) {
                BottomNavTab.TWITTER -> twitterFilter
                BottomNavTab.REDDIT -> redditFilter
                BottomNavTab.ALL -> twitterFilter
                BottomNavTab.MAP -> twitterFilter
            }
        }
    }
    val activeBanner by remember {
        derivedStateOf {
            when (selectedTab) {
                BottomNavTab.TWITTER -> twitterBanner
                BottomNavTab.REDDIT -> redditBanner
                else -> null
            }
        }
    }
    // Tab-aware SAVED count: Twitter (and the Twitter-backed ALL/MAP tabs) report the
    // live Twitter feed count; Reddit has no count wired and keeps the legacy `000`.
    val activeCount by remember {
        derivedStateOf {
            when (selectedTab) {
                BottomNavTab.TWITTER -> twitterCount
                BottomNavTab.REDDIT -> 0
                BottomNavTab.ALL -> twitterCount
                BottomNavTab.MAP -> twitterCount
            }
        }
    }

    LaunchedEffect(Unit) {
        services.syncErrorBus.events.collect { event ->
            when (event) {
                is SyncErrorEvent.TwitterAuth401 -> {
                    twitterBanner = BannerState(
                        source = BookmarkSource.Twitter,
                        kicker = "ERR · RECONNECT TWITTER",
                        detail = "Twitter session expired. Tap to reconnect.",
                        ctaLabel = "RECONNECT",
                    )
                }
                is SyncErrorEvent.RedditAuth401 -> {
                    redditBanner = BannerState(
                        source = BookmarkSource.Reddit,
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
        services.snackbarBus.events.collect { event ->
            when (event) {
                is SnackbarEvent.UndoableDelete -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "BOOKMARK DELETED",
                        actionLabel = "UNDO",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        when (event.source) {
                            BookmarkSource.Twitter -> bookmarksViewModel.undoDelete(event.id)
                            BookmarkSource.Reddit -> redditViewModel.undoDelete(event.id)
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
            itemCount = activeCount,
        ),
        onTabSelected = { selectedTab = it },
        onSearchQueryChange = { searchQuery = it },
        onSearchActiveChange = { active ->
            isSearchActive = active
            if (active) {
                navController.navigate(Screens.SEARCHSCREEN.name)
            }
        },
        onChipToggled = { id ->
            when (selectedTab) {
                BottomNavTab.TWITTER, BottomNavTab.ALL, BottomNavTab.MAP -> bookmarksViewModel.onTypeChipToggled(id)
                BottomNavTab.REDDIT -> redditViewModel.onTypeChipToggled(id)
            }
        },
        onSortClick = { /* sort dialog deferred to a follow-up slice */ },
        onBannerCta = {
            when (activeBanner?.source) {
                // X reconnect routes through the dedicated ConnectX destination
                // so the Custom Tabs + deep-link round-trip lives in one place.
                BookmarkSource.Twitter -> navController.navigate(Screens.CONNECTX.name)
                BookmarkSource.Reddit -> {
                    val intent: Intent? = redditViewModel.authIntent()
                    intent?.let {
                        try {
                            context.startActivity(it)
                        } catch (e: ActivityNotFoundException) {
                            Timber.e(e, "No activity to handle OAuth intent")
                            snackbarScope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "NO BROWSER FOUND. INSTALL A WEB BROWSER TO OPEN LINKS.",
                                    duration = SnackbarDuration.Long,
                                )
                            }
                        }
                    }
                }
                null -> Unit
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
