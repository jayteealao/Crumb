package com.github.jayteealao.twitter.screens

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.github.jayteealao.crumbs.designsystem.components.PopupAction
import com.github.jayteealao.crumbs.designsystem.components.TagEditorDialog
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.models.ContentType
import com.github.jayteealao.twitter.models.TweetData
import kotlinx.collections.immutable.persistentListOf
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
    onRefresh: () -> Unit,
    onConnectClick: () -> Unit,
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
                        LaunchedEffect(id) { onLoadTags(id) }
                        val tags = uiState.tagsMap[id] ?: emptyList()
                        val bookmark = tweetData.toBookmark(tags)
                        CrumbsBookmarkCard(
                            bookmark = bookmark,
                            onCardClick = onCardClick,
                            onLongPress = onLongPress,
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
    val loggedIn by loginViewModel.isAccessTokenAvailable.collectAsState()
    val tagsMap by bookmarksViewModel.tagsForTweet.collectAsState()
    val allTags by bookmarksViewModel.allTags.collectAsState()
    val isRefreshing by bookmarksViewModel.isRefreshing.collectAsState()

    var popupBookmark by remember { mutableStateOf<Bookmark?>(null) }
    var popupAnchor by remember { mutableStateOf(Offset.Zero) }
    var showTagEditor by remember { mutableStateOf(false) }

    LaunchedEffect(loggedIn) {
        if (loggedIn) {
            Timber.d("Triggering buildDatabase after login")
            bookmarksViewModel.buildDatabase()
        }
    }
    LaunchedEffect(twitterAuthCode) {
        if (!twitterAuthCode.isNullOrBlank()) {
            loginViewModel.getAccessToken(twitterAuthCode.split("code=").last())
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
            popupBookmark = bookmark
            popupAnchor = offset
        },
        onLoadTags = { id -> bookmarksViewModel.loadTagsForTweet(id) },
        onRefresh = { bookmarksViewModel.refresh() },
        onConnectClick = { context.startActivity(loginViewModel.authIntent()) },
        contentPadding = contentPadding,
    )

    popupBookmark?.let { bookmark ->
        CrumbsLongPressPopup(
            visible = true,
            onDismiss = { popupBookmark = null },
            anchorOffsetPx = popupAnchor,
            actions = persistentListOf(
                PopupAction(
                    id = "tag",
                    label = "TAG",
                    hint = "Add",
                    icon = Icons.Default.LocalOffer,
                    isPrimary = true,
                    onClick = {
                        Timber.d("Twitter long-press: TAG ${bookmark.id}")
                        showTagEditor = true
                    },
                ),
                PopupAction(
                    id = "open",
                    label = "OPEN",
                    hint = "Url",
                    icon = Icons.Default.Language,
                    onClick = {
                        Timber.d("Twitter long-press: OPEN ${bookmark.id}")
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(bookmark.sourceUrl))
                        context.startActivity(intent)
                    },
                ),
                PopupAction(
                    id = "share",
                    label = "SHARE",
                    hint = "Link",
                    icon = Icons.Default.Share,
                    onClick = {
                        Timber.d("Twitter long-press: SHARE ${bookmark.id}")
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, bookmark.sourceUrl)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share tweet"))
                    },
                ),
                PopupAction(
                    id = "logout",
                    label = "LOGOUT",
                    hint = "Exit",
                    icon = Icons.Default.Logout,
                    isDanger = true,
                    onClick = {
                        Timber.d("Twitter long-press: LOGOUT")
                        bookmarksViewModel.logout()
                    },
                ),
            ),
        )
    }

    if (showTagEditor && popupBookmark != null) {
        val current = (tagsMap[popupBookmark!!.id] ?: emptyList()).toImmutableList()
        TagEditorDialog(
            isVisible = showTagEditor,
            currentTags = current,
            availableTags = allTags.toImmutableList(),
            onDismiss = {
                showTagEditor = false
                popupBookmark = null
            },
            onSave = { tags ->
                bookmarksViewModel.saveTags(popupBookmark!!.id, tags.toList())
                showTagEditor = false
                popupBookmark = null
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
            onRefresh = {},
            onConnectClick = {},
        )
    }
}
