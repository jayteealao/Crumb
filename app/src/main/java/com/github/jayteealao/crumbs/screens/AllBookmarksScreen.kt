package com.github.jayteealao.crumbs.screens

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.github.jayteealao.crumbs.Screens
import com.github.jayteealao.crumbs.designsystem.components.CrumbsBookmarkCard
import com.github.jayteealao.crumbs.designsystem.components.CrumbsLongPressPopup
import com.github.jayteealao.crumbs.designsystem.components.EmptyState
import com.github.jayteealao.crumbs.designsystem.components.LoadingCard
import com.github.jayteealao.crumbs.designsystem.components.TagEditorDialog
import com.github.jayteealao.crumbs.designsystem.components.bookmarkPopupActions
import com.github.jayteealao.crumbs.designsystem.components.rememberLongPressState
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography
import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.models.ContentType
import com.github.jayteealao.reddit.models.RedditPostData
import com.github.jayteealao.reddit.screens.RedditViewModel
import com.github.jayteealao.reddit.screens.toBookmark
import com.github.jayteealao.twitter.models.TweetData
import com.github.jayteealao.twitter.screens.BookmarksViewModel
import com.github.jayteealao.twitter.screens.LoginViewModel
import com.github.jayteealao.twitter.screens.toBookmark
import kotlinx.collections.immutable.toImmutableList
import timber.log.Timber

@androidx.compose.runtime.Immutable
data class AllBookmarksUiState(
    val twitterConnected: Boolean = false,
    val redditConnected: Boolean = false,
    val tagsMap: Map<String, List<String>> = emptyMap(),
)

@Composable
fun AllBookmarksScreen(
    uiState: AllBookmarksUiState,
    twitterItems: LazyPagingItems<TweetData>?,
    redditItems: LazyPagingItems<RedditPostData>?,
    onCardClick: (String) -> Unit,
    onLongPress: (Bookmark, Offset) -> Unit,
    onConnectAccountClick: () -> Unit,
    onLoadTags: (String) -> Unit,
    onLoadTagsForIds: (List<String>) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier,
) {
    val hasAnySource = uiState.twitterConnected || uiState.redditConnected
    val typography = LocalCrumbsTypography.current
    val colors = LocalCrumbsColors.current

    if (!hasAnySource) {
        EmptyState(
            title = "NO CRUMBS YET",
            message = "Connect an account to start saving bookmarks.",
            actionText = "CONNECT AN ACCOUNT",
            onActionClick = onConnectAccountClick,
            modifier = modifier
                .testTag("all-bookmarks-empty"),
        )
        return
    }

    // Single batch tag load per page-snapshot change — replaces per-item LaunchedEffect.
    val twitterIds = remember(twitterItems?.itemCount) {
        val count = twitterItems?.itemCount ?: 0
        (0 until count).mapNotNull { twitterItems?.peek(it)?.tweet?.id }
    }
    LaunchedEffect(twitterIds) {
        if (twitterIds.isNotEmpty()) onLoadTagsForIds(twitterIds)
    }

    val redditIds = remember(redditItems?.itemCount) {
        val count = redditItems?.itemCount ?: 0
        (0 until count).mapNotNull { redditItems?.peek(it)?.post?.id }
    }
    LaunchedEffect(redditIds) {
        if (redditIds.isNotEmpty()) onLoadTagsForIds(redditIds)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("all-bookmarks-screen"),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("all-bookmarks-feed"),
            contentPadding = contentPadding,
        ) {
            if (uiState.twitterConnected && twitterItems != null) {
                item("twitter-header") {
                    Text(
                        text = "TWITTER",
                        style = typography.titleSection,
                        color = colors.ink,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                renderPagingSection(
                    items = twitterItems,
                    tagsMap = uiState.tagsMap,
                    onCardClick = onCardClick,
                    onLongPress = onLongPress,
                    idOf = { it.tweet.id },
                    toBookmark = { item, tags -> item.toBookmark(tags) },
                    emptyTitle = "NO TWITTER CRUMBS YET",
                    emptyMessage = "Start saving tweets to see them here.",
                )
            }
            if (uiState.redditConnected && redditItems != null) {
                item("reddit-header") {
                    Text(
                        text = "REDDIT",
                        style = typography.titleSection,
                        color = colors.ink,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                renderPagingSection(
                    items = redditItems,
                    tagsMap = uiState.tagsMap,
                    onCardClick = onCardClick,
                    onLongPress = onLongPress,
                    idOf = { it.post.id },
                    toBookmark = { item, tags -> item.toBookmark(tags) },
                    emptyTitle = "NO REDDIT CRUMBS YET",
                    emptyMessage = "Start saving Reddit posts to see them here.",
                )
            }
        }
    }
}

private fun <T : Any> androidx.compose.foundation.lazy.LazyListScope.renderPagingSection(
    items: LazyPagingItems<T>,
    tagsMap: Map<String, List<String>>,
    onCardClick: (String) -> Unit,
    onLongPress: (Bookmark, Offset) -> Unit,
    idOf: (T) -> String,
    toBookmark: (T, List<String>) -> Bookmark,
    emptyTitle: String,
    emptyMessage: String,
) {
    when (items.loadState.refresh) {
        is LoadState.Loading -> items(3) {
            LoadingCard(
                hasImage = it % 2 == 0,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        is LoadState.Error -> item("error") {
            EmptyState(
                title = "ERROR LOADING CRUMBS",
                message = "SOMETHING WENT WRONG. PULL TO REFRESH TO TRY AGAIN.",
                modifier = Modifier.padding(16.dp),
            )
        }
        else -> Unit
    }
    items(
        count = items.itemCount,
        key = items.itemKey { idOf(it) },
    ) { index ->
        val item = items[index]
        if (item != null) {
            val id = idOf(item)
            val tags = tagsMap[id] ?: emptyList()
            val bookmark = toBookmark(item, tags)
            CrumbsBookmarkCard(
                bookmark = bookmark,
                onCardClick = onCardClick,
                onLongPress = onLongPress,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
    if (items.loadState.refresh is LoadState.NotLoading && items.itemCount == 0) {
        item("empty") {
            EmptyState(
                title = emptyTitle,
                message = emptyMessage,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

// Route — owns Hilt ViewModel injection, paging, popup state, tag editor.

@Composable
fun AllBookmarksRoute(
    contentPadding: PaddingValues,
    loginViewModel: LoginViewModel = hiltViewModel(),
    redditViewModel: RedditViewModel = hiltViewModel(),
    bookmarksViewModel: BookmarksViewModel = hiltViewModel(),
    navController: NavController? = null,
) {
    val context = LocalContext.current

    val twitterItems = bookmarksViewModel.pagingFlowData().collectAsLazyPagingItems()
    val twitterLoggedIn by loginViewModel.isAccessTokenAvailable.collectAsState()
    val redditItems = redditViewModel.pagingFlowData().collectAsLazyPagingItems()
    val redditLoggedIn by redditViewModel.isAccessTokenAvailable.collectAsState()

    val tagsMap by bookmarksViewModel.tagsForTweet.collectAsState()
    val allTags by bookmarksViewModel.allTags.collectAsState()

    val lps = rememberLongPressState()

    AllBookmarksScreen(
        uiState = AllBookmarksUiState(
            twitterConnected = twitterLoggedIn,
            redditConnected = redditLoggedIn,
            tagsMap = tagsMap,
        ),
        twitterItems = if (twitterLoggedIn) twitterItems else null,
        redditItems = if (redditLoggedIn) redditItems else null,
        onCardClick = { url ->
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            context.startActivity(intent)
        },
        onLongPress = { bookmark, offset ->
            lps.bookmark = bookmark
            lps.anchor = offset
        },
        onConnectAccountClick = {
            navController?.navigate(Screens.LOGINSCREEN.name)
        },
        onLoadTags = { id -> bookmarksViewModel.loadTagsForTweet(id) },
        onLoadTagsForIds = { ids -> bookmarksViewModel.loadTagsForItems(ids) },
        contentPadding = contentPadding,
    )

    lps.bookmark?.let { bookmark ->
        CrumbsLongPressPopup(
            visible = true,
            onDismiss = { lps.bookmark = null },
            anchorOffsetPx = lps.anchor,
            actions = bookmarkPopupActions(
                onTag = {
                    Timber.d("AllBookmarks long-press: TAG")
                    lps.showTagEditor = true
                },
                onOpen = {
                    Timber.d("AllBookmarks long-press: OPEN")
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(bookmark.sourceUrl))
                    context.startActivity(intent)
                },
                onShare = {
                    Timber.d("AllBookmarks long-press: SHARE")
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, bookmark.sourceUrl)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share bookmark"))
                },
                onDelete = {
                    Timber.d("AllBookmarks long-press: DELETE")
                    when (bookmark.source) {
                        BookmarkSource.Twitter -> bookmarksViewModel.softDelete(bookmark.id)
                        BookmarkSource.Reddit -> redditViewModel.softDelete(bookmark.id)
                    }
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

// `RedditPostData.toBookmark` lives in feature/reddit
// (com.github.jayteealao.reddit.screens.toBookmark). Importing it here keeps
// the conversion logic single-sourced — the duplicate that used to live below
// drifted from the reddit copy and is removed.

@Preview(name = "AllBookmarks Empty Light", showBackground = true)
@Composable
private fun PreviewAllBookmarksEmptyLight() {
    CrumbsTheme(darkTheme = false) {
        AllBookmarksScreen(
            uiState = AllBookmarksUiState(),
            twitterItems = null,
            redditItems = null,
            onCardClick = {},
            onLongPress = { _, _ -> },
            onConnectAccountClick = {},
            onLoadTags = {},
            onLoadTagsForIds = {},
        )
    }
}

@Preview(name = "AllBookmarks Empty Dark", showBackground = true)
@Composable
private fun PreviewAllBookmarksEmptyDark() {
    CrumbsTheme(darkTheme = true) {
        AllBookmarksScreen(
            uiState = AllBookmarksUiState(),
            twitterItems = null,
            redditItems = null,
            onCardClick = {},
            onLongPress = { _, _ -> },
            onConnectAccountClick = {},
            onLoadTags = {},
            onLoadTagsForIds = {},
        )
    }
}
