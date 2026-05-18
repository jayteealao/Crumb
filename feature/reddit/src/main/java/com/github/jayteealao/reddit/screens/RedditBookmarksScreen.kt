package com.github.jayteealao.reddit.screens

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
import androidx.compose.material.icons.filled.Share
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
import com.github.jayteealao.crumbs.designsystem.components.CrumbsLongPressPopup
import com.github.jayteealao.crumbs.designsystem.components.EmptyState
import com.github.jayteealao.crumbs.designsystem.components.LoadingCard
import com.github.jayteealao.crumbs.designsystem.components.PopupAction
import com.github.jayteealao.crumbs.designsystem.components.TagEditorDialog
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.models.ContentType
import com.github.jayteealao.reddit.models.RedditPostData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import timber.log.Timber

@androidx.compose.runtime.Immutable
data class RedditBookmarksUiState(
    val loggedIn: Boolean = false,
    val tagsMap: Map<String, List<String>> = emptyMap(),
)

@Composable
fun RedditBookmarksScreen(
    uiState: RedditBookmarksUiState,
    pagedPosts: LazyPagingItems<RedditPostData>?,
    onCardClick: (String) -> Unit,
    onLongPress: (Bookmark, Offset) -> Unit,
    onLoadTags: (String) -> Unit,
    onConnectClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier,
) {
    if (!uiState.loggedIn) {
        EmptyState(
            title = "CONNECT TO REDDIT",
            message = "Sign in to start saving and viewing your bookmarks.",
            actionText = "LOGIN TO REDDIT",
            onActionClick = onConnectClick,
            modifier = modifier
                .testTag("reddit-bookmarks-empty"),
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("reddit-bookmarks-screen"),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("reddit-bookmarks-feed"),
            contentPadding = contentPadding,
        ) {
            when (pagedPosts?.loadState?.refresh) {
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
            if (pagedPosts != null) {
                items(
                    count = pagedPosts.itemCount,
                    key = pagedPosts.itemKey { it.post.id },
                ) { index ->
                    val postData = pagedPosts[index]
                    if (postData != null) {
                        val id = postData.post.id
                        LaunchedEffect(id) { onLoadTags(id) }
                        val tags = uiState.tagsMap[id] ?: emptyList()
                        val bookmark = postData.toBookmark(tags)
                        CrumbsBookmarkCard(
                            bookmark = bookmark,
                            onCardClick = onCardClick,
                            onLongPress = onLongPress,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }
                when (pagedPosts.loadState.append) {
                    is LoadState.Loading -> item {
                        LoadingCard(
                            hasImage = false,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    else -> Unit
                }
                if (pagedPosts.loadState.refresh is LoadState.NotLoading &&
                    pagedPosts.itemCount == 0
                ) {
                    item {
                        EmptyState(
                            title = "NO CRUMBS YET",
                            message = "Start saving Reddit posts to see them here.",
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RedditBookmarksRoute(
    navController: NavController,
    contentPadding: PaddingValues,
    redditAuthCode: String? = "",
    redditViewModel: RedditViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val pagedPosts = redditViewModel.pagingFlowData().collectAsLazyPagingItems()
    val loggedIn by redditViewModel.isAccessTokenAvailable.collectAsState()
    val tagsMap by redditViewModel.tagsForTweet.collectAsState()
    val allTags by redditViewModel.allTags.collectAsState()

    var popupBookmark by remember { mutableStateOf<Bookmark?>(null) }
    var popupAnchor by remember { mutableStateOf(Offset.Zero) }
    var showTagEditor by remember { mutableStateOf(false) }

    LaunchedEffect(loggedIn) {
        if (loggedIn) {
            Timber.d("Triggering buildDatabase after Reddit login")
            redditViewModel.buildDatabase()
        }
    }
    LaunchedEffect(redditAuthCode) {
        if (!redditAuthCode.isNullOrBlank()) {
            redditViewModel.getAccessToken(redditAuthCode.split("code=").last())
        }
    }

    RedditBookmarksScreen(
        uiState = RedditBookmarksUiState(
            loggedIn = loggedIn,
            tagsMap = tagsMap,
        ),
        pagedPosts = if (loggedIn) pagedPosts else null,
        onCardClick = { url ->
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            context.startActivity(intent)
        },
        onLongPress = { bookmark, offset ->
            popupBookmark = bookmark
            popupAnchor = offset
        },
        onLoadTags = { id -> redditViewModel.loadTagsForTweet(id) },
        onConnectClick = { context.startActivity(redditViewModel.authIntent()) },
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
                        Timber.d("Reddit long-press: TAG ${bookmark.id}")
                        showTagEditor = true
                    },
                ),
                PopupAction(
                    id = "open",
                    label = "OPEN",
                    hint = "Url",
                    icon = Icons.Default.Language,
                    onClick = {
                        Timber.d("Reddit long-press: OPEN ${bookmark.id}")
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
                        Timber.d("Reddit long-press: SHARE ${bookmark.id}")
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, bookmark.sourceUrl)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share post"))
                    },
                ),
                PopupAction(
                    id = "delete",
                    label = "DELETE",
                    hint = "Remove",
                    icon = Icons.Default.Delete,
                    isDanger = true,
                    onClick = {
                        Timber.d("Reddit long-press: DELETE ${bookmark.id}")
                        redditViewModel.softDelete(bookmark.id)
                        popupBookmark = null
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
                redditViewModel.saveTags(popupBookmark!!.id, tags.toList())
                showTagEditor = false
                popupBookmark = null
            },
        )
    }
}

private fun RedditPostData.toBookmark(tags: List<String> = emptyList()): Bookmark {
    val contentType = when {
        post.isVideo -> ContentType.Video
        post.thumbnail != null && post.thumbnail !in listOf("self", "default", "nsfw") -> ContentType.Image
        !post.isSelf -> ContentType.Link
        else -> ContentType.Text
    }
    val imageUrl = when {
        post.thumbnail != null && post.thumbnail !in listOf("self", "default", "nsfw") -> post.thumbnail
        contentType == ContentType.Image -> post.url
        else -> null
    }
    val videoUrl = if (post.isVideo) post.url else null
    return Bookmark(
        id = post.id,
        source = BookmarkSource.Reddit,
        author = "u/${post.author}",
        title = post.title,
        previewText = if (post.selftext.isNotBlank()) post.selftext else post.title,
        imageUrl = imageUrl,
        videoUrl = videoUrl,
        contentType = contentType,
        savedAt = post.createdUtc * 1000,
        tags = tags,
        isThread = false,
        threadCount = 1,
        isDeleted = false,
        sourceUrl = "https://reddit.com${post.permalink}",
    )
}

@Preview(name = "Reddit Bookmarks Empty Light", showBackground = true)
@Composable
private fun PreviewRedditEmptyLight() {
    CrumbsTheme(darkTheme = false) {
        RedditBookmarksScreen(
            uiState = RedditBookmarksUiState(loggedIn = false),
            pagedPosts = null,
            onCardClick = {},
            onLongPress = { _, _ -> },
            onLoadTags = {},
            onConnectClick = {},
        )
    }
}

@Preview(name = "Reddit Bookmarks Empty Dark", showBackground = true)
@Composable
private fun PreviewRedditEmptyDark() {
    CrumbsTheme(darkTheme = true) {
        RedditBookmarksScreen(
            uiState = RedditBookmarksUiState(loggedIn = false),
            pagedPosts = null,
            onCardClick = {},
            onLongPress = { _, _ -> },
            onLoadTags = {},
            onConnectClick = {},
        )
    }
}
