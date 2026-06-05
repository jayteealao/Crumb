package com.github.jayteealao.twitter.screens

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.github.jayteealao.crumbs.designsystem.components.BookmarkActionsOverlay
import com.github.jayteealao.crumbs.designsystem.components.CrumbsBookmarkCard
import com.github.jayteealao.crumbs.designsystem.components.CrumbsImageViewer
import com.github.jayteealao.crumbs.designsystem.components.CrumbsVideoViewer
import com.github.jayteealao.crumbs.designsystem.components.CrumbsButton
import com.github.jayteealao.crumbs.designsystem.components.EmptyState
import com.github.jayteealao.crumbs.designsystem.components.LoadingCard
import com.github.jayteealao.crumbs.designsystem.components.rememberLongPressState
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.crumbs.models.ContentType
import com.github.jayteealao.twitter.data.toBookmark as toBookmarkImpl
import com.github.jayteealao.twitter.models.TweetData
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber

@androidx.compose.runtime.Immutable
data class TwitterBookmarksUiState(
    val loggedIn: Boolean = false,
    val isRefreshing: Boolean = false,
    val tagsMap: Map<String, List<String>> = emptyMap(),
)

/**
 * Paged feed of Twitter bookmarks with pull-to-refresh. Shows a connect-account prompt when the
 * user is not linked to Twitter, skeleton loading cards while the first page loads, and an error
 * state on failure.
 *
 * @param uiState Snapshot of login state, refresh indicator, and the tag map.
 * @param pagedBookmarks Paged [TweetData] from the database, or `null` when the user is not logged in.
 * @param onCardClick Called with the source URL when the user taps a bookmark card.
 * @param onLongPress Called with the [Bookmark] and screen-space anchor on long-press for the action overlay.
 * @param onLoadTags Called with a single tweet id to lazily load its tags (legacy single-item path).
 * @param onLoadTagsForIds Called with a batch of tweet ids when a new page snapshot is visible.
 * @param onRefresh Called when the user pulls to refresh; triggers a server-side bookmark poll.
 * @param onConnectClick Called when the user taps the connect CTA in the logged-out empty state.
 * @param onConfirmDeletePending Called with the tweet id when the user confirms a swipe-to-delete.
 * @param onCancelDeletePending Called with the tweet id when the user cancels a pending delete.
 * @param onRequestMediaRefetch Called with a tweet id when a media-less card scrolls into view, so
 *   the data layer can attempt a single-tweet media re-fetch for the legacy (pre-cutover) corpus.
 * @param onLinkClick Called with the outbound link URL when the user taps a card's link preview;
 *   opens the destination in the external browser (the rest of the card keeps the permalink tap).
 * @param onRequestLinkRefetch Called with a tweet id when an external-link card with no preview data
 *   scrolls into view, so the data layer can pull its server-enriched url entity (legacy corpus).
 * @param onQuoteClick Called with the quoted tweet's permalink when the user taps a card's quoted
 *   sub-card; opens it in the external browser (the rest of the card keeps the parent permalink).
 * @param onRequestQuoteRefetch Called with a tweet id when a card whose quote is unavailable
 *   (referenced but no body) scrolls into view, so the data layer can pull the server-written
 *   quoted body (legacy corpus).
 * @param videoPlayer The shared ExoPlayer, bound to whichever card matches [activeVideoId] (or to
 *   the full-screen viewer when [videoViewerOpen]); null until the first video play.
 * @param activeVideoId Tweet id of the single active inline video, or null when none is playing.
 * @param videoViewerOpen Whether the full-screen video viewer is open (it borrows [videoPlayer]).
 * @param onVideoPlay Called when the user taps a video card's play badge — make it the active video.
 * @param onVideoExpand Called when the user taps a video card's expand affordance.
 * @param onVideoViewerDismiss Called when the full-screen video viewer is dismissed.
 * @param lazyListState Hoisted so the host can detach the player when the active video scrolls away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwitterBookmarksScreen(
    uiState: TwitterBookmarksUiState,
    pagedBookmarks: LazyPagingItems<TweetData>?,
    onCardClick: (String) -> Unit,
    onLongPress: (Bookmark, Offset) -> Unit,
    onLoadTags: (String) -> Unit,
    onLoadTagsForIds: (List<String>) -> Unit,
    onRefresh: () -> Unit,
    onConnectClick: () -> Unit,
    onConfirmDeletePending: (String) -> Unit = {},
    onCancelDeletePending: (String) -> Unit = {},
    onRequestMediaRefetch: (String) -> Unit = {},
    onLinkClick: (String) -> Unit = {},
    onRequestLinkRefetch: (String) -> Unit = {},
    onQuoteClick: (String) -> Unit = {},
    onRequestQuoteRefetch: (String) -> Unit = {},
    videoPlayer: Player? = null,
    activeVideoId: String? = null,
    videoViewerOpen: Boolean = false,
    onVideoPlay: (Bookmark) -> Unit = {},
    onVideoExpand: (Bookmark) -> Unit = {},
    onVideoViewerDismiss: () -> Unit = {},
    lazyListState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier,
) {
    if (!uiState.loggedIn) {
        EmptyState(
            title = "CONNECT TO TWITTER",
            message = "Sign in to start saving and viewing your bookmarks.",
            actionText = "CONNECT TWITTER",
            onActionClick = onConnectClick,
            modifier = modifier
                .testTag("twitter-bookmarks-empty"),
        )
        return
    }

    // Single batch tag load per page-snapshot change — replaces per-item LaunchedEffect.
    val itemIds = remember(pagedBookmarks?.itemCount) {
        val count = pagedBookmarks?.itemCount ?: 0
        (0 until count).mapNotNull { pagedBookmarks?.peek(it)?.tweet?.id }
    }
    LaunchedEffect(itemIds) {
        if (itemIds.isNotEmpty()) onLoadTagsForIds(itemIds)
    }

    // Full-screen image viewer state — non-null while open. Set from a card's
    // onImageClick (the tapped image's index into that bookmark's imageUrls).
    var imageViewer by remember { mutableStateOf<ImageViewerTarget?>(null) }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .testTag("twitter-bookmarks-screen"),
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("twitter-bookmarks-feed"),
            contentPadding = contentPadding,
        ) {
            when (pagedBookmarks?.loadState?.refresh) {
                is LoadState.Loading -> items(5) {
                    LoadingCard(
                        hasImage = it % 2 == 0,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                is LoadState.Error -> item {
                    EmptyState(
                        title = "ERROR LOADING CRUMBS",
                        message = "SOMETHING WENT WRONG. PULL TO REFRESH.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
                else -> Unit
            }
            if (pagedBookmarks != null) {
                items(
                    count = pagedBookmarks.itemCount,
                    key = pagedBookmarks.itemKey { it.tweet.id },
                ) { index ->
                    val tweetData = pagedBookmarks[index]
                    if (tweetData != null) {
                        val id = tweetData.tweet.id
                        val tags = uiState.tagsMap[id] ?: emptyList()
                        val bookmark = remember(tweetData, tags) { tweetData.toBookmark(tags) }
                        // Legacy media re-fetch (per AC): a media-less card — or a video
                        // card whose stream variants never synced (column added empty for
                        // the legacy corpus) — scrolling into view asks the data layer to
                        // re-pull this tweet's media. The ViewModel dedupes attempts per
                        // session; on success Room's InvalidationTracker re-emits this card,
                        // so the retry-on-revisit is automatic. On failure it stays text-only.
                        val needsMediaRefetch = (bookmark.imageUrls.isEmpty() && bookmark.videoUrl == null) ||
                            (bookmark.contentType == ContentType.Video && bookmark.videoVariants.isEmpty())
                        if (needsMediaRefetch) {
                            LaunchedEffect(id) { onRequestMediaRefetch(id) }
                        }
                        // Legacy link re-fetch: an external-looking tweet (text carries a URL) that
                        // has no resolved link entity yet — its server-enriched url row may have
                        // landed since this tweet was first synced. One attempt per tweet per session
                        // (deduped in the VM); on success the url row populates and the card re-emits
                        // as a Link with a preview. Bounded to non-media cards so image/video tweets
                        // that also contain a link are not re-pulled here.
                        val needsLinkRefetch = bookmark.linkUrl == null &&
                            bookmark.contentType != ContentType.Image &&
                            bookmark.contentType != ContentType.Video &&
                            bookmark.previewText.contains("http")
                        if (needsLinkRefetch) {
                            LaunchedEffect("link-$id") { onRequestLinkRefetch(id) }
                        }
                        // Legacy quote re-fetch: the tweet references a quote (quotedTweetId
                        // set) but its body never landed (quotedText null → "unavailable").
                        // The server may have written the body since this tweet was synced.
                        // One attempt per tweet per session (deduped in the VM); on success
                        // the quoted body lands and the card re-emits with the quote.
                        val needsQuoteRefetch = bookmark.quotedTweetId != null && bookmark.quotedText == null
                        if (needsQuoteRefetch) {
                            LaunchedEffect("quote-$id") { onRequestQuoteRefetch(id) }
                        }
                        // Bind the shared player to this card only when it is the single
                        // active video AND the full-screen viewer is closed, so exactly one
                        // surface holds the player at a time.
                        val cardPlayer = if (!videoViewerOpen && id == activeVideoId) videoPlayer else null
                        CrumbsBookmarkCard(
                            bookmark = bookmark,
                            onCardClick = onCardClick,
                            onLongPress = onLongPress,
                            onConfirmDeletePending = onConfirmDeletePending,
                            onCancelDeletePending = onCancelDeletePending,
                            // Show the bookmark's DB rowid (zero-padded) in the index
                            // strip instead of the default "000". `%03d` is min-width,
                            // so rowids above 999 render in full (e.g. 1234), untruncated.
                            indexOverride = "%03d".format(bookmark.dbNumber),
                            onImageClick = { tappedIndex ->
                                imageViewer = ImageViewerTarget(bookmark.imageUrls, tappedIndex)
                            },
                            onLinkClick = onLinkClick,
                            onQuoteClick = onQuoteClick,
                            videoPlayer = cardPlayer,
                            onVideoPlay = { onVideoPlay(bookmark) },
                            onVideoExpand = { onVideoExpand(bookmark) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }
                when (pagedBookmarks.loadState.append) {
                    is LoadState.Loading -> item {
                        LoadingCard(
                            hasImage = false,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    else -> Unit
                }
                if (pagedBookmarks.loadState.refresh is LoadState.NotLoading &&
                    pagedBookmarks.itemCount == 0
                ) {
                    item {
                        EmptyState(
                            title = "NO CRUMBS YET",
                            message = "Start saving tweets to see them here.",
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }

        imageViewer?.let { target ->
            CrumbsImageViewer(
                imageUrls = target.images,
                initialIndex = target.index,
                onDismiss = { imageViewer = null },
            )
        }

        // Full-screen video expand surface. Hosts the shared player while open; the inline
        // card is passed null (above) so only one surface binds the player at a time.
        if (videoViewerOpen) {
            CrumbsVideoViewer(
                player = videoPlayer,
                onDismiss = onVideoViewerDismiss,
            )
        }
    }
}

/** Open-state for the full-screen image viewer: the tweet's photos + the tapped page. */
@androidx.compose.runtime.Immutable
private data class ImageViewerTarget(
    val images: List<String>,
    val index: Int,
)

// Route — owns Hilt ViewModel injection, paging, popup state, tag editor.

/**
 * Navigation entry point for the Twitter tab inside [HomeScreen]. Injects ViewModels, collects
 * paged bookmark data, and manages the long-press [BookmarkActionsOverlay] for open, share,
 * delete, and tag-edit actions.
 *
 * @param navController Used to navigate to the ConnectX destination from the logged-out empty state.
 * @param contentPadding Padding from the parent scaffold passed down to the lazy list.
 * @param twitterAuthCode OAuth auth code forwarded from the deep-link intent, if present.
 * @param bookmarksViewModel Provides paging data, sync status, and tag/delete operations.
 * @param loginViewModel Provides legacy access-token state (kept for parity with the Reddit path).
 */
@Composable
fun TwitterBookmarksRoute(
    navController: NavController,
    contentPadding: PaddingValues,
    twitterAuthCode: String? = "",
    bookmarksViewModel: BookmarksViewModel = hiltViewModel(),
    loginViewModel: LoginViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val pagedBookmarks = bookmarksViewModel.pagingFlowData().collectAsLazyPagingItems()
    // Server-driven gate: sync_status.linked is the authoritative source after the
    // server-side cutover. The legacy isAccessTokenAvailable read Prefs.accessCode,
    // which is permanently empty post-cutover and would freeze the screen on the
    // "CONNECT TO TWITTER" empty state even after a successful server-side link.
    val syncStatus by bookmarksViewModel.syncStatus.collectAsStateWithLifecycle()
    val loggedIn = syncStatus?.linked == true
    val tagsMap by bookmarksViewModel.tagsForTweet.collectAsStateWithLifecycle()
    val allTags by bookmarksViewModel.allTags.collectAsStateWithLifecycle()
    val isRefreshing by bookmarksViewModel.isRefreshing.collectAsStateWithLifecycle()

    val lps = rememberLongPressState()

    // Inline-video plumbing. The list state is hoisted so the active video can be detached
    // when it scrolls out of view; the shared player is read straight off the VM (it is
    // null until the first play, and recomposition is driven by the two state flows below).
    val lazyListState = rememberLazyListState()
    val activeVideoId by bookmarksViewModel.activeVideoId.collectAsStateWithLifecycle()
    val videoViewerOpen by bookmarksViewModel.videoViewerOpen.collectAsStateWithLifecycle()

    // Background/foreground → pause/resume the shared player on ON_PAUSE/ON_RESUME.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> bookmarksViewModel.onAppPaused()
                Lifecycle.Event.ON_RESUME -> bookmarksViewModel.onAppResumed()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // When the active video scrolls out of view, detach the surface + stop playback so the
    // feed never keeps an off-screen player alive (the single-player memory invariant).
    LaunchedEffect(activeVideoId) {
        val id = activeVideoId ?: return@LaunchedEffect
        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.any { it.key == id } }
            .distinctUntilChanged()
            .collect { visible -> if (!visible) bookmarksViewModel.clearActiveVideo() }
    }

    // Server-side polling owns initial fetch (dailyPoll / triggerPoll fan-out
    // from oauthCallback). The local Firestore one-shot read in Repository.init
    // hydrates the UI on app start.
    LaunchedEffect(loggedIn) {
        if (loggedIn) {
            bookmarksViewModel.refresh()
        }
    }

    TwitterBookmarksScreen(
        uiState = TwitterBookmarksUiState(
            loggedIn = loggedIn,
            isRefreshing = isRefreshing,
            tagsMap = tagsMap,
        ),
        pagedBookmarks = if (loggedIn) pagedBookmarks else null,
        onCardClick = { url ->
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            context.startActivity(intent)
        },
        onLongPress = { bookmark, offset ->
            lps.bookmark = bookmark
            lps.anchor = offset
        },
        onLoadTags = { id -> bookmarksViewModel.loadTagsForTweet(id) },
        onLoadTagsForIds = { ids -> bookmarksViewModel.loadTagsForItems(ids) },
        onRefresh = { bookmarksViewModel.refresh() },
        // Server-side OAuth: navigate to the dedicated CONNECTX route so the
        // Custom Tabs + deep-link round-trip lives in one place. Route name
        // mirrors the app-level Screens.CONNECTX enum.
        onConnectClick = { navController.navigate("CONNECTX") },
        onConfirmDeletePending = { id -> bookmarksViewModel.confirmDeletePending(id) },
        onCancelDeletePending = { id -> bookmarksViewModel.cancelDeletePending(id) },
        onRequestMediaRefetch = { id -> bookmarksViewModel.refetchMediaIfMissing(id) },
        // Link preview tap → open the outbound destination in the EXTERNAL browser
        // (not Custom Tabs, per the AC). CATEGORY_BROWSABLE + an ActivityNotFound
        // guard mirror the standard external-link launch; the whole-card tap keeps
        // the tweet-permalink behaviour via onCardClick above.
        onLinkClick = { url ->
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addCategory(Intent.CATEGORY_BROWSABLE)
            runCatching { context.startActivity(intent) }
                .onFailure { Timber.w(it, "No browser to open link: $url") }
        },
        onRequestLinkRefetch = { id -> bookmarksViewModel.refetchLinksIfMissing(id) },
        // Quoted sub-card tap → open the quoted tweet's permalink in the EXTERNAL
        // browser (mirrors onLinkClick); the whole-card tap keeps the parent permalink
        // via onCardClick above. The non-consuming gesture in the card routes them apart.
        onQuoteClick = { url ->
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addCategory(Intent.CATEGORY_BROWSABLE)
            runCatching { context.startActivity(intent) }
                .onFailure { Timber.w(it, "No browser to open quoted tweet: $url") }
        },
        onRequestQuoteRefetch = { id -> bookmarksViewModel.refetchQuotesIfMissing(id) },
        videoPlayer = bookmarksViewModel.player,
        activeVideoId = activeVideoId,
        videoViewerOpen = videoViewerOpen,
        onVideoPlay = { bookmark -> bookmarksViewModel.playVideo(bookmark) },
        onVideoExpand = { bookmark -> bookmarksViewModel.expandVideo(bookmark) },
        onVideoViewerDismiss = { bookmarksViewModel.closeVideoViewer() },
        lazyListState = lazyListState,
        contentPadding = contentPadding,
    )

    val activeBookmark = lps.bookmark
    BookmarkActionsOverlay(
        visible = activeBookmark != null,
        bookmark = activeBookmark,
        currentTags = (tagsMap[activeBookmark?.id] ?: emptyList()).toImmutableList(),
        availableTags = allTags.toImmutableList(),
        onDismiss = { lps.dismiss() },
        onActionSelect = { action ->
            val b = activeBookmark ?: return@BookmarkActionsOverlay
            when (action.id) {
                "open" -> {
                    Timber.d("Twitter long-press: OPEN")
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(b.sourceUrl))
                    context.startActivity(intent)
                }
                "share" -> {
                    Timber.d("Twitter long-press: SHARE")
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, b.sourceUrl)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share tweet"))
                }
                "delete" -> {
                    Timber.d("Twitter long-press: DELETE")
                    bookmarksViewModel.softDelete(b.id)
                }
            }
        },
        onTagsSave = { tags ->
            val b = activeBookmark ?: return@BookmarkActionsOverlay
            bookmarksViewModel.saveTags(b.id, tags.toList())
        },
    )
}

/**
 * Public helper used by AllBookmarksScreen + tests. Kept as a top-level extension
 * in this package so existing imports from cross-module consumers remain valid.
 * The mapping logic lives in [com.github.jayteealao.twitter.data.toBookmark].
 */
fun TweetData.toBookmark(tags: List<String> = emptyList()): Bookmark = toBookmarkImpl(tags)

@Preview(name = "Twitter Bookmarks Empty Light", showBackground = true)
@Composable
private fun PreviewTwitterEmptyLight() {
    CrumbsTheme(darkTheme = false) {
        TwitterBookmarksScreen(
            uiState = TwitterBookmarksUiState(loggedIn = false),
            pagedBookmarks = null,
            onCardClick = {},
            onLongPress = { _, _ -> },
            onLoadTags = {},
            onLoadTagsForIds = {},
            onRefresh = {},
            onConnectClick = {},
        )
    }
}

@Preview(name = "Twitter Bookmarks Empty Dark", showBackground = true)
@Composable
private fun PreviewTwitterEmptyDark() {
    CrumbsTheme(darkTheme = true) {
        TwitterBookmarksScreen(
            uiState = TwitterBookmarksUiState(loggedIn = false),
            pagedBookmarks = null,
            onCardClick = {},
            onLongPress = { _, _ -> },
            onLoadTags = {},
            onLoadTagsForIds = {},
            onRefresh = {},
            onConnectClick = {},
        )
    }
}
