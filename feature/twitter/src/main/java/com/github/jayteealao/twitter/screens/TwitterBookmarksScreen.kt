package com.github.jayteealao.twitter.screens

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.github.jayteealao.crumbs.designsystem.components.BookmarkActionsOverlay
import com.github.jayteealao.crumbs.designsystem.components.CrumbsBookmarkCard
import com.github.jayteealao.crumbs.designsystem.components.CrumbsButton
import com.github.jayteealao.crumbs.designsystem.components.EmptyState
import com.github.jayteealao.crumbs.designsystem.components.LoadingCard
import com.github.jayteealao.crumbs.designsystem.components.rememberLongPressState
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.models.ContentType
import com.github.jayteealao.twitter.models.TweetData
import com.github.jayteealao.twitter.util.parseTweetTimestamp
import kotlinx.collections.immutable.toImmutableList
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
    contentPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier,
) {
    if (!uiState.loggedIn) {
        EmptyState(
            title = "CONNECT TO TWITTER",
            message = "Sign in to start saving and viewing your bookmarks.",
            actionText = "LOGIN TO TWITTER",
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

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .testTag("twitter-bookmarks-screen"),
    ) {
        LazyColumn(
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
                        val bookmark = tweetData.toBookmark(tags)
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
    }
}

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
 * Public helper used by AllBookmarksScreen + tests. Kept as a top-level
 * extension so cross-module consumers can map TweetData -> Bookmark without
 * dragging in a ViewModel.
 */
fun TweetData.toBookmark(tags: List<String> = emptyList()): Bookmark {
    val contentType = when {
        media.any { it.type == "video" } -> ContentType.Video
        media.any { it.type == "photo" } -> ContentType.Image
        tweet.text.contains("http") -> ContentType.Link
        else -> ContentType.Text
    }
    val imageUrl = media.firstOrNull { it.type == "photo" }?.url
    val videoUrl = media.firstOrNull { it.type == "video" }?.url
    // Prefer the server-stamped retrieval time; fall back to the tweet's own creation time;
    // when neither is available/parseable, use the unknown-time sentinel rather than
    // fabricating "now" (which produced the long-standing wrong "X months ago" label).
    val timestamp = tweet.retrievedAt
        ?: parseTweetTimestamp(tweet.createdAt)
        ?: Bookmark.UNKNOWN_TIME
    val title = tweet.text.lines().firstOrNull()?.take(100) ?: tweet.text.take(100)
    return Bookmark(
        id = tweet.id,
        source = BookmarkSource.Twitter,
        author = "@${user.username}",
        title = title,
        previewText = tweet.text,
        imageUrl = imageUrl,
        videoUrl = videoUrl,
        contentType = contentType,
        savedAt = timestamp,
        tags = tags,
        isThread = false,
        threadCount = 1,
        isDeleted = false,
        pendingDelete = tweet.pendingDelete,
        sourceUrl = "https://twitter.com/${user.username}/status/${tweet.id}",
        // The card's index strip shows this as the per-row "number in the DB".
        dbNumber = dbRowId,
    )
}

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
