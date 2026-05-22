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
import com.github.jayteealao.crumbs.designsystem.components.CrumbsBookmarkCard
import com.github.jayteealao.crumbs.designsystem.components.CrumbsButton
import com.github.jayteealao.crumbs.designsystem.components.CrumbsLongPressPopup
import com.github.jayteealao.crumbs.designsystem.components.EmptyState
import com.github.jayteealao.crumbs.designsystem.components.LoadingCard
import com.github.jayteealao.crumbs.designsystem.components.TagEditorDialog
import com.github.jayteealao.crumbs.designsystem.components.bookmarkPopupActions
import com.github.jayteealao.crumbs.designsystem.components.rememberLongPressState
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.models.ContentType
import com.github.jayteealao.twitter.models.TweetData
import kotlinx.collections.immutable.toImmutableList
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale

@androidx.compose.runtime.Immutable
data class TwitterBookmarksUiState(
    val loggedIn: Boolean = false,
    val isRefreshing: Boolean = false,
    val tagsMap: Map<String, List<String>> = emptyMap(),
)

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
    val loggedIn by loginViewModel.isAccessTokenAvailable.collectAsStateWithLifecycle()
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

    lps.bookmark?.let { bookmark ->
        CrumbsLongPressPopup(
            visible = true,
            onDismiss = { lps.bookmark = null },
            anchorOffsetPx = lps.anchor,
            actions = bookmarkPopupActions(
                onTag = {
                    Timber.d("Twitter long-press: TAG")
                    lps.showTagEditor = true
                },
                onOpen = {
                    Timber.d("Twitter long-press: OPEN")
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(bookmark.sourceUrl))
                    context.startActivity(intent)
                },
                onShare = {
                    Timber.d("Twitter long-press: SHARE")
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, bookmark.sourceUrl)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share tweet"))
                },
                onDelete = {
                    Timber.d("Twitter long-press: DELETE")
                    bookmarksViewModel.softDelete(bookmark.id)
                    lps.bookmark = null
                },
            ),
        )
    }

    if (lps.showTagEditor && lps.bookmark != null) {
        val current = (tagsMap[lps.bookmark!!.id] ?: emptyList()).toImmutableList()
        TagEditorDialog(
            isVisible = lps.showTagEditor,
            currentTags = current,
            availableTags = allTags.toImmutableList(),
            onDismiss = { lps.dismiss() },
            onSave = { tags ->
                bookmarksViewModel.saveTags(lps.bookmark!!.id, tags.toList())
                lps.dismiss()
            },
        )
    }
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
    val timestamp = try {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        formatter.parse(tweet.createdAt)?.time ?: System.currentTimeMillis()
    } catch (e: Exception) {
        System.currentTimeMillis()
    }
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
