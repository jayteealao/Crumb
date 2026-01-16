package com.github.jayteealao.twitter.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
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
import com.github.jayteealao.twitter.models.TweetData
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun TwitterBookmarksScreen(
    navController: NavController,
    twitterAuthCode: String? = "",
    bookmarksViewModel: BookmarksViewModel = hiltViewModel(),
    loginViewModel: LoginViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val pagedBookmarks = bookmarksViewModel.pagingFlowData().collectAsLazyPagingItems()
    val loggedIn by loginViewModel.isAccessTokenAvailable.collectAsState()
    val tagsMap by bookmarksViewModel.tagsForTweet.collectAsState()
    val allTags by bookmarksViewModel.allTags.collectAsState()

    // Quick actions state
    var showQuickActions by remember { mutableStateOf(false) }
    var selectedTweetData by remember { mutableStateOf<TweetData?>(null) }
    var showTagEditor by remember { mutableStateOf(false) }

    LaunchedEffect(loggedIn) {
        // Trigger bookmark sync when user successfully logs in
        if (loggedIn) {
            Timber.d("Triggering buildDatabase after login")
            bookmarksViewModel.buildDatabase()
        }
    }

    LaunchedEffect(true) {
        if (!twitterAuthCode.isNullOrBlank()) {
            loginViewModel.getAccessToken(twitterAuthCode.split("code=").last())
        }
    }

    when {
        !loggedIn -> {
            LinkTwitter(intentFn = loginViewModel.authIntent())
        }

        else -> {
            Box(modifier = Modifier.fillMaxSize()) {
                // Display bookmarks
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                // Loading state
                when (pagedBookmarks.loadState.refresh) {
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
                                title = "Error loading bookmarks",
                                message = "Something went wrong. Pull to refresh to try again.",
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    else -> Unit
                }

                // Bookmarks
                items(
                    pagedBookmarks,
                    key = { it.tweet.id }
                ) { tweetData ->
                    if (tweetData != null) {
                        // Load tags for this tweet
                        LaunchedEffect(tweetData.tweet.id) {
                            bookmarksViewModel.loadTagsForTweet(tweetData.tweet.id)
                        }

                        val tags = tagsMap[tweetData.tweet.id] ?: emptyList()
                        val bookmark = tweetData.toBookmark(tags)

                        CrumbsBookmarkCard(
                            bookmark = bookmark,
                            onCardClick = { url ->
                                // Open URL externally
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                context.startActivity(intent)
                            },
                            onLongPress = {
                                selectedTweetData = tweetData
                                showQuickActions = true
                            },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                // Append loading state
                when (pagedBookmarks.loadState.append) {
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
                if (pagedBookmarks.loadState.refresh is LoadState.NotLoading && pagedBookmarks.itemCount == 0) {
                    item {
                        EmptyState(
                            title = "No bookmarks yet",
                            message = "Start saving tweets to see them here. Go to Twitter and use the share menu to save tweets.",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // Quick action menu
            if (showQuickActions && selectedTweetData != null) {
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
                            val url = selectedTweetData?.let {
                                "https://twitter.com/${it.user.username}/status/${it.tweet.id}"
                            }
                            if (url != null) {
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                context.startActivity(intent)
                            }
                        },
                        QuickAction(
                            label = "Share",
                            icon = Icons.Default.Share
                        ) {
                            val url = selectedTweetData?.let {
                                "https://twitter.com/${it.user.username}/status/${it.tweet.id}"
                            }
                            if (url != null) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, url)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share tweet"))
                            }
                        }
                    )
                )
            }

            // Tag editor dialog
            if (showTagEditor && selectedTweetData != null) {
                val currentTags = tagsMap[selectedTweetData!!.tweet.id] ?: emptyList()

                TagEditorDialog(
                    isVisible = showTagEditor,
                    currentTags = currentTags,
                    availableTags = allTags,
                    onDismiss = {
                        showTagEditor = false
                        showQuickActions = false
                    },
                    onSave = { newTags ->
                        bookmarksViewModel.saveTags(selectedTweetData!!.tweet.id, newTags)
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
fun LinkTwitter(intentFn: Intent) {
    val context = LocalContext.current

    EmptyState(
        title = "Connect to Twitter",
        message = "Sign in to Twitter to start saving and viewing your bookmarks",
        actionText = "Login To Twitter",
        onActionClick = {
            context.startActivity(intentFn)
        }
    )
}

/**
 * Converts a TweetData model to a Bookmark model for display
 */
fun TweetData.toBookmark(tags: List<String> = emptyList()): Bookmark {
    // Determine content type
    val contentType = when {
        media.any { it.type == "video" } -> ContentType.Video
        media.any { it.type == "photo" } -> ContentType.Image
        tweet.text.contains("http") -> ContentType.Link
        else -> ContentType.Text
    }

    // Get first media URL if available
    val imageUrl = media.firstOrNull { it.type == "photo" }?.url
    val videoUrl = media.firstOrNull { it.type == "video" }?.url

    // Parse timestamp
    val timestamp = try {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        formatter.parse(tweet.createdAt)?.time ?: System.currentTimeMillis()
    } catch (e: Exception) {
        System.currentTimeMillis()
    }

    // Extract title (first line or up to 100 chars)
    val title = tweet.text.lines().firstOrNull()?.take(100) ?: tweet.text.take(100)

    // Preview text (full text, will be truncated by component)
    val previewText = tweet.text

    return Bookmark(
        id = tweet.id,
        source = BookmarkSource.Twitter,
        author = "@${user.username}",
        title = title,
        previewText = previewText,
        imageUrl = imageUrl,
        videoUrl = videoUrl,
        contentType = contentType,
        savedAt = timestamp,
        tags = tags,
        isThread = false, // TODO: Detect threads
        threadCount = 1,
        isDeleted = false,
        sourceUrl = "https://twitter.com/${user.username}/status/${tweet.id}"
    )
}
