package com.github.jayteealao.crumbs.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.github.jayteealao.reddit.screens.RedditViewModel
import com.github.jayteealao.twitter.models.TweetData
import com.github.jayteealao.twitter.screens.BookmarksViewModel
import com.github.jayteealao.twitter.screens.LoginViewModel
import com.github.jayteealao.twitter.screens.toBookmark

/**
 * Screen displaying all bookmarks from both Twitter and Reddit sources
 */
@Composable
fun AllBookmarksScreen(
    loginViewModel: LoginViewModel = hiltViewModel(),
    redditViewModel: RedditViewModel = hiltViewModel(),
    bookmarksViewModel: BookmarksViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // Twitter bookmarks
    val twitterPosts = bookmarksViewModel.pagingFlowData().collectAsLazyPagingItems()
    val twitterLoggedIn by loginViewModel.isAccessTokenAvailable.collectAsState()

    // Reddit bookmarks
    val redditPosts = redditViewModel.pagingFlowData().collectAsLazyPagingItems()
    val redditLoggedIn by redditViewModel.isAccessTokenAvailable.collectAsState()

    // Tags
    val tagsMap by bookmarksViewModel.tagsForTweet.collectAsState()
    val allTags by bookmarksViewModel.allTags.collectAsState()

    // Quick actions state
    var showQuickActions by remember { mutableStateOf(false) }
    var selectedBookmark by remember { mutableStateOf<Bookmark?>(null) }
    var selectedBookmarkId by remember { mutableStateOf<String?>(null) }
    var showTagEditor by remember { mutableStateOf(false) }

    // Check if either service is logged in
    val hasAnySource = twitterLoggedIn || redditLoggedIn

    if (!hasAnySource) {
        // No sources connected
        EmptyState(
            title = "No Sources Connected",
            message = "Connect to Twitter or Reddit to see your bookmarks here",
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Twitter Section
                if (twitterLoggedIn) {
                    // Twitter section header
                    item {
                        Text(
                            text = "Twitter",
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    // Twitter loading state
                    when (twitterPosts.loadState.refresh) {
                        is LoadState.Loading -> {
                            items(3) {
                                LoadingCard(
                                    hasImage = it % 2 == 0,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                        is LoadState.Error -> {
                            item {
                                EmptyState(
                                    title = "Error loading Twitter bookmarks",
                                    message = "Something went wrong. Pull to refresh to try again.",
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        else -> Unit
                    }

                    // Twitter posts
                    items(
                        twitterPosts,
                        key = { "twitter_${it.tweet.id}" }
                    ) { tweetData ->
                        if (tweetData != null) {
                            LaunchedEffect(tweetData.tweet.id) {
                                bookmarksViewModel.loadTagsForTweet(tweetData.tweet.id)
                            }

                            val tags = tagsMap[tweetData.tweet.id] ?: emptyList()
                            val bookmark = tweetData.toBookmark(tags)

                            CrumbsBookmarkCard(
                                bookmark = bookmark,
                                onCardClick = { url ->
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                    context.startActivity(intent)
                                },
                                onLongPress = {
                                    selectedBookmark = bookmark
                                    selectedBookmarkId = tweetData.tweet.id
                                    showQuickActions = true
                                },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    // Twitter empty state (if no items but loaded)
                    if (twitterPosts.loadState.refresh is LoadState.NotLoading && twitterPosts.itemCount == 0) {
                        item {
                            EmptyState(
                                title = "No Twitter bookmarks yet",
                                message = "Start bookmarking tweets to see them here",
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                // Reddit Section
                if (redditLoggedIn) {
                    // Reddit section header
                    item {
                        Text(
                            text = "Reddit",
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    // Reddit loading state
                    when (redditPosts.loadState.refresh) {
                        is LoadState.Loading -> {
                            items(3) {
                                LoadingCard(
                                    hasImage = it % 2 == 0,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                        is LoadState.Error -> {
                            item {
                                EmptyState(
                                    title = "Error loading Reddit posts",
                                    message = "Something went wrong. Pull to refresh to try again.",
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        else -> Unit
                    }

                    // Reddit posts
                    items(
                        redditPosts,
                        key = { "reddit_${it.post.id}" }
                    ) { postData ->
                        if (postData != null) {
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
                                    selectedBookmark = bookmark
                                    selectedBookmarkId = postData.post.id
                                    showQuickActions = true
                                },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    // Reddit empty state (if no items but loaded)
                    if (redditPosts.loadState.refresh is LoadState.NotLoading && redditPosts.itemCount == 0) {
                        item {
                            EmptyState(
                                title = "No Reddit posts yet",
                                message = "Start saving Reddit posts to see them here",
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }

            // Quick action menu
            if (showQuickActions && selectedBookmark != null && selectedBookmarkId != null) {
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
                            val url = selectedBookmark!!.sourceUrl
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            context.startActivity(intent)
                        },
                        QuickAction(
                            label = "Share",
                            icon = Icons.Default.Share
                        ) {
                            val url = selectedBookmark!!.sourceUrl
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, url)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share bookmark"))
                        }
                    )
                )
            }

            // Tag editor dialog
            if (showTagEditor && selectedBookmarkId != null) {
                val currentTags = tagsMap[selectedBookmarkId!!] ?: emptyList()

                TagEditorDialog(
                    isVisible = showTagEditor,
                    currentTags = currentTags,
                    availableTags = allTags,
                    onDismiss = {
                        showTagEditor = false
                        showQuickActions = false
                    },
                    onSave = { newTags ->
                        bookmarksViewModel.saveTags(selectedBookmarkId!!, newTags)
                        showTagEditor = false
                        showQuickActions = false
                    }
                )
            }
        }
    }
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
