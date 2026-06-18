package com.github.jayteealao.crumbs.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.components.CrumbsBookmarkCard
import com.github.jayteealao.crumbs.designsystem.components.CrumbsFilterBar
import com.github.jayteealao.crumbs.designsystem.components.CrumbsTopBar
import com.github.jayteealao.crumbs.designsystem.components.TopBarAction
import com.github.jayteealao.crumbs.designsystem.layouts.HomeScaffold
import com.github.jayteealao.crumbs.designsystem.modifiers.dashedDivider
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography
import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.models.ContentType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Displays a Twitter thread: the root bookmark as a full card followed by compact numbered reply
 * rows. Uses [HomeScaffold] with the filter bar showing "VIEWING THREAD" and no bottom nav bar.
 *
 * @param uiState One of [ThreadDetailUiState.Loading], [ThreadDetailUiState.Error], or
 *   [ThreadDetailUiState.Loaded]; drives which body content is shown.
 * @param onClose Called when the user taps the close action in the top bar to pop this destination.
 * @param onRootCardClick Called with the root [Bookmark] when the user taps the root card to open its URL.
 */
@Composable
fun ThreadDetailScreen(
    uiState: ThreadDetailUiState,
    onClose: () -> Unit,
    onRootCardClick: (Bookmark) -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeScaffold(
        modifier = modifier.testTag("thread-detail-screen"),
        topBar = {
            CrumbsTopBar(
                kickerText = null,
                trailing = TopBarAction.Close,
                onTrailingClick = onClose,
            )
        },
        filterBar = {
            // Use the existing filter bar surface to host the brutalist
            // "VIEWING THREAD" cell; countLabel + sortLabel stay blank because
            // ThreadDetail has no count/sort affordances.
            CrumbsFilterBar(
                countLabel = "",
                filterLabel = "VIEWING THREAD",
                sortLabel = "",
                onFilterClick = {},
                onSortClick = {},
                modifier = Modifier.testTag("thread-detail-filterbar"),
            )
        },
        bottomBar = {},
    ) { padding ->
        ThreadDetailBody(
            uiState = uiState,
            onRootCardClick = onRootCardClick,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@Composable
private fun ThreadDetailBody(
    uiState: ThreadDetailUiState,
    onRootCardClick: (Bookmark) -> Unit,
    modifier: Modifier = Modifier,
) {
    val typography = LocalCrumbsTypography.current
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current

    Box(modifier = modifier.background(colors.background)) {
        when (uiState) {
            ThreadDetailUiState.Loading -> Text(
                text = "Loading thread…",
                style = typography.bodyMono,
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .padding(spacing.lg)
                    .testTag("thread-detail-loading"),
            )
            is ThreadDetailUiState.Error -> Text(
                text = uiState.message,
                style = typography.bodyMono,
                color = colors.ink,
                modifier = Modifier
                    .padding(spacing.lg)
                    .testTag("thread-detail-error"),
            )
            is ThreadDetailUiState.Loaded -> LoadedThread(
                root = uiState.root,
                replies = uiState.replies,
                onRootCardClick = onRootCardClick,
            )
        }
    }
}

@Composable
private fun LoadedThread(
    root: Bookmark,
    replies: ImmutableList<Bookmark>,
    onRootCardClick: (Bookmark) -> Unit,
) {
    val spacing = LocalCrumbsSpacing.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("thread-detail-feed"),
        contentPadding = PaddingValues(
            start = spacing.lg,
            end = spacing.lg,
            top = spacing.md,
            bottom = spacing.lg,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item("root") {
            CrumbsBookmarkCard(
                bookmark = root,
                onCardClick = { onRootCardClick(root) },
                index = 0,
                isExpanded = true,
                indexOverride = "THREAD · ${replies.size} ↕",
                modifier = Modifier.testTag("thread-root-card"),
            )
        }
        if (replies.isEmpty()) {
            item("empty-replies") {
                EmptyRepliesNote()
            }
        } else {
            itemsIndexed(replies, key = { _, r -> r.id }) { index, reply ->
                ThreadReplyRow(
                    index = index + 1,
                    text = reply.previewText,
                    modifier = Modifier.testTag("thread-reply-row-${index + 1}"),
                )
            }
        }
    }
}

@Composable
private fun EmptyRepliesNote() {
    val colors = LocalCrumbsColors.current
    val typography = LocalCrumbsTypography.current
    val spacing = LocalCrumbsSpacing.current
    Text(
        text = "No bookmarked replies in this thread.",
        style = typography.bodyMono,
        color = colors.onSurfaceVariant,
        modifier = Modifier
            .padding(top = spacing.lg)
            .testTag("thread-detail-empty-replies"),
    )
}

/**
 * Screen-local compact reply row. Per option-d-screens.jsx DThread lines
 * 614-630: 22 dp number-bullet box + mono text + dashed separator. Not
 * promoted to `core/designsystem/` until a second consumer materializes.
 */
@Composable
private fun ThreadReplyRow(
    index: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCrumbsColors.current
    val typography = LocalCrumbsTypography.current
    val spacing = LocalCrumbsSpacing.current
    val stroke = LocalCrumbsStroke.current
    Column(modifier = modifier.fillMaxWidth().testTag("thread-reply-row")) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            modifier = Modifier.padding(vertical = spacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(colors.ink),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "%02d".format(index),
                    style = typography.metaMono,
                    color = colors.surface,
                )
            }
            Text(
                text = text,
                style = typography.bodyMono,
                color = colors.ink,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .dashedDivider(
                    color = colors.ink,
                    strokeWidth = stroke.hairline,
                    dashLengthDp = 4.dp,
                    gapDp = 3.dp,
                ),
        )
    }
}

@Preview(name = "ThreadDetail Loaded Light", showBackground = true)
@Composable
private fun PreviewThreadLoadedLight() {
    val root = sampleBookmark()
    CrumbsTheme(darkTheme = false) {
        ThreadDetailScreen(
            uiState = ThreadDetailUiState.Loaded(
                root = root,
                replies = persistentListOf(
                    root.copy(id = "r1", previewText = "Reply one — short answer."),
                    root.copy(id = "r2", previewText = "Reply two — longer answer with more text."),
                ),
            ),
            onClose = {},
            onRootCardClick = {},
        )
    }
}

@Preview(name = "ThreadDetail Empty Dark", showBackground = true)
@Composable
private fun PreviewThreadEmptyDark() {
    CrumbsTheme(darkTheme = true) {
        ThreadDetailScreen(
            uiState = ThreadDetailUiState.Loaded(
                root = sampleBookmark(),
                replies = persistentListOf(),
            ),
            onClose = {},
            onRootCardClick = {},
        )
    }
}

private fun sampleBookmark() = Bookmark(
    id = "root-1",
    source = BookmarkSource.Twitter,
    author = "@compose",
    title = "Brutalist thread root",
    previewText = "Root tweet preview text body content goes here.",
    contentType = ContentType.Thread,
    savedAt = 1730000000000L,
    isThread = true,
    threadCount = 3,
    sourceUrl = "https://twitter.com/compose/status/root-1",
)
