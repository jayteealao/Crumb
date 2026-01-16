package com.github.jayteealao.reddit.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.items
import com.github.jayteealao.crumbs.designsystem.components.CrumbsBookmarkCard
import com.github.jayteealao.crumbs.designsystem.components.EmptyState
import com.github.jayteealao.crumbs.designsystem.components.LoadingCard
import com.github.jayteealao.crumbs.designsystem.components.QuickAction
import com.github.jayteealao.crumbs.designsystem.components.QuickActionMenu
import com.github.jayteealao.crumbs.designsystem.components.TagEditorDialog
import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.models.ContentType
import com.github.jayteealao.reddit.models.RedditPostData
import com.github.jayteealao.twitter.screens.BookmarksViewModel
import timber.log.Timber

/**
 * Screen displaying saved Reddit posts
 */
@Composable
fun RedditBookmarksScreen(
    navController: NavController,
    redditAuthCode: String? = "",
    redditViewModel: RedditViewModel = hiltViewModel(),
    bookmarksViewModel: BookmarksViewModel = hiltViewModel() // For tags
) {
    val context = LocalContext.current
    val pagedPosts = redditViewModel.pagingFlowData().collectAsLazyPagingItems()
    val loggedIn by redditViewModel.isAccessTokenAvailable.collectAsState()
    val tagsMap by bookmarksViewModel.tagsForTweet.collectAsState()
    val allTags by bookmarksViewModel.allTags.collectAsState()

    // Quick actions state
    var showQuickActions by remember { mutableStateOf(false) }
    var selectedPostData by remember { mutableStateOf<RedditPostData?>(null) }
    var showTagEditor by remember { mutableStateOf(false) }

    LaunchedEffect(loggedIn) {
        if (loggedIn) {
            Timber.d("Triggering buildDatabase after Reddit login")
            redditViewModel.buildDatabase()
        }
    }

    LaunchedEffect(true) {
        if (!redditAuthCode.isNullOrBlank()) {
            redditViewModel.getAccessToken(redditAuthCode.split("code=").last())
        }
    }

    when {
        !loggedIn -> {
            LinkReddit(intentFn = redditViewModel.authIntent())
        }

        else -> {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Loading state
                    when (pagedPosts.loadState.refresh) {
                        is LoadState.Loading -> {
                            items(5) {
                                LoadingCard(
                                    hasImage = it % 2 == 0,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                        is LoadState.Error -> {
                            item {
                                EmptyState(
                                    title = "Error loading posts",
                                    message = "Something went wrong. Pull to refresh to try again.",
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        else -> Unit
                    }

                    // Posts
                    items(
                        pagedPosts,
                        key = { it.post.id }
                    ) { postData ->
                        if (postData != null) {
                            // Load tags for this post
                            LaunchedEffect(postData.post.id) {
                                bookmarksViewModel.loadTagsForTweet(postData.post.id)
                            }

                            val tags = tagsMap[postData.post.id] ?: emptyList()
                            val bookmark = postData.toBookmark(tags)

                            CrumbsBookmarkCard(
                                bookmark = bookmark,
                                onCardClick = { url ->
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                    context.startActivity(intent)
                                },
                                onLongPress = {
                                    selectedPostData = postData
                                    showQuickActions = true
                                },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    // Append loading state
                    when (pagedPosts.loadState.append) {
                        is LoadState.Loading -> {
                            item {
                                LoadingCard(
                                    hasImage = false,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                        else -> Unit
                    }

                    // Empty state
                    if (pagedPosts.loadState.refresh is LoadState.NotLoading && pagedPosts.itemCount == 0) {
                        item {
                            EmptyState(
                                title = "No saved posts yet",
                                message = "Start saving Reddit posts to see them here. Go to Reddit and save posts.",
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                // Quick action menu
                if (showQuickActions && selectedPostData != null) {
                    QuickActionMenu(
                        visible = showQuickActions,
                        onDismiss = { showQuickActions = false },
                        actions = listOf(
                            QuickAction(
                                label = "Add Tags",
                                icon = Icons.Default.LocalOffer
                            ) {
                                showTagEditor = true
                            },
                            QuickAction(
                                label = "Open URL",
                                icon = Icons.Default.Language
                            ) {
                                val url = "https://reddit.com${selectedPostData!!.post.permalink}"
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                context.startActivity(intent)
                            },
                            QuickAction(
                                label = "Share",
                                icon = Icons.Default.Share
                            ) {
                                val url = "https://reddit.com${selectedPostData!!.post.permalink}"
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, url)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share post"))
                            }
                        )
                    )
                }

                // Tag editor dialog
                if (showTagEditor && selectedPostData != null) {
                    val currentTags = tagsMap[selectedPostData!!.post.id] ?: emptyList()

                    TagEditorDialog(
                        isVisible = showTagEditor,
                        currentTags = currentTags,
                        availableTags = allTags,
                        onDismiss = {
                            showTagEditor = false
                            showQuickActions = false
                        },
                        onSave = { newTags ->
                            bookmarksViewModel.saveTags(selectedPostData!!.post.id, newTags)
                            showTagEditor = false
                            showQuickActions = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LinkReddit(intentFn: Intent) {
    val context = LocalContext.current

    EmptyState(
        title = "Connect to Reddit",
        message = "Sign in to Reddit to start saving and viewing your bookmarks",
        actionText = "Login To Reddit",
        onActionClick = {
            context.startActivity(intentFn)
        }
    )
}

/**
 * Convert RedditPostData to Bookmark model for display
 */
private fun RedditPostData.toBookmark(tags: List<String> = emptyList()): Bookmark {
    // Determine content type
    val contentType = when {
        post.isVideo -> ContentType.Video
        post.thumbnail != null && post.thumbnail !in listOf("self", "default", "nsfw") -> ContentType.Image
        !post.isSelf -> ContentType.Link
        else -> ContentType.Text
    }

    // Get image URL from thumbnail or URL (if image post)
    val imageUrl = if (post.thumbnail != null && post.thumbnail !in listOf("self", "default", "nsfw")) {
        post.thumbnail
    } else if (contentType == ContentType.Image) {
        post.url
    } else null

    // Video URL
    val videoUrl = if (post.isVideo) post.url else null

    // Timestamp (Reddit uses Unix timestamp in seconds, we need milliseconds)
    val timestamp = post.createdUtc * 1000

    return Bookmark(
        id = post.id,
        source = BookmarkSource.Reddit,
        author = "u/${post.author}",
        title = post.title,
        previewText = if (post.selftext.isNotBlank()) post.selftext else post.title,
        imageUrl = imageUrl,
        videoUrl = videoUrl,
        contentType = contentType,
        savedAt = timestamp,
        tags = tags,
        isThread = false,
        threadCount = 1,
        isDeleted = false,
        sourceUrl = "https://reddit.com${post.permalink}"
    )
}
