package com.github.jayteealao.reddit.screens

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.github.jayteealao.crumbs.designsystem.components.BookmarkActionsOverlay
import com.github.jayteealao.crumbs.designsystem.components.CrumbsBookmarkCard
import com.github.jayteealao.crumbs.designsystem.components.EmptyState
import com.github.jayteealao.crumbs.designsystem.components.LoadingCard
import com.github.jayteealao.crumbs.designsystem.components.rememberLongPressState
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.models.ContentType
import com.github.jayteealao.reddit.models.RedditPostData
import kotlinx.collections.immutable.toImmutableList
import timber.log.Timber

@androidx.compose.runtime.Immutable
data class RedditBookmarksUiState(
    val loggedIn: Boolean = false,
    val tagsMap: Map<String, List<String>> = emptyMap(),
)

/**
 * Paged feed of Reddit saved posts. Shows a connect-account prompt when the user is not linked
 * to Reddit, skeleton loading cards while the first page loads, and an error state on failure.
 *
 * @param uiState Snapshot of login state and the tag map for loaded posts.
 * @param pagedPosts Paged [RedditPostData] from the database, or `null` when the user is not logged in.
 * @param onCardClick Called with the source URL when the user taps a bookmark card.
 * @param onLongPress Called with the [Bookmark] and screen-space anchor on long-press for the action overlay.
 * @param onLoadTags Called with a single post id to lazily load its tags (legacy single-item path).
 * @param onLoadTagsForIds Called with a batch of post ids when a new page snapshot is visible.
 * @param onConnectClick Called when the user taps the connect CTA in the logged-out empty state.
 */
@Composable
fun RedditBookmarksScreen(
    uiState: RedditBookmarksUiState,
    pagedPosts: LazyPagingItems<RedditPostData>?,
    onCardClick: (String) -> Unit,
    onLongPress: (Bookmark, Offset) -> Unit,
    onLoadTags: (String) -> Unit,
    onLoadTagsForIds: (List<String>) -> Unit,
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

    // Single batch tag load per page-snapshot change — replaces per-item LaunchedEffect.
    val itemIds = remember(pagedPosts?.itemCount) {
        val count = pagedPosts?.itemCount ?: 0
        (0 until count).mapNotNull { pagedPosts?.peek(it)?.post?.id }
    }
    LaunchedEffect(itemIds) {
        if (itemIds.isNotEmpty()) onLoadTagsForIds(itemIds)
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

/**
 * Navigation entry point for the Reddit tab inside [HomeScreen]. Injects [RedditViewModel],
 * collects paged saved-post data, and manages the long-press [BookmarkActionsOverlay] for open,
 * share, delete, and tag-edit actions.
 *
 * @param navController Available for future navigation needs (currently unused by this route).
 * @param contentPadding Padding from the parent scaffold passed down to the lazy list.
 * @param redditAuthCode OAuth auth code forwarded from the deep-link intent, triggers token exchange.
 * @param redditViewModel Provides login state, paging data, and tag/delete operations.
 */
@Composable
fun RedditBookmarksRoute(
    navController: NavController,
    contentPadding: PaddingValues,
    redditAuthCode: String? = "",
    redditViewModel: RedditViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val pagedPosts = redditViewModel.pagingFlowData().collectAsLazyPagingItems()
    val loggedIn by redditViewModel.isAccessTokenAvailable.collectAsStateWithLifecycle()
    val tagsMap by redditViewModel.tagsForTweet.collectAsStateWithLifecycle()
    val allTags by redditViewModel.allTags.collectAsStateWithLifecycle()

    val lps = rememberLongPressState()

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
            lps.bookmark = bookmark
            lps.anchor = offset
        },
        onLoadTags = { id -> redditViewModel.loadTagsForTweet(id) },
        onLoadTagsForIds = { ids -> redditViewModel.loadTagsForItems(ids) },
        onConnectClick = { context.startActivity(redditViewModel.authIntent()) },
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
                    Timber.d("Reddit long-press: OPEN")
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(b.sourceUrl))
                    context.startActivity(intent)
                }
                "share" -> {
                    Timber.d("Reddit long-press: SHARE")
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, b.sourceUrl)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share post"))
                }
                "delete" -> {
                    Timber.d("Reddit long-press: DELETE")
                    redditViewModel.softDelete(b.id)
                }
            }
        },
        onTagsSave = { tags ->
            val b = activeBookmark ?: return@BookmarkActionsOverlay
            redditViewModel.saveTags(b.id, tags.toList())
        },
    )
}

fun RedditPostData.toBookmark(tags: List<String> = emptyList()): Bookmark {
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
            onLoadTagsForIds = {},
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
            onLoadTagsForIds = {},
            onConnectClick = {},
        )
    }
}
