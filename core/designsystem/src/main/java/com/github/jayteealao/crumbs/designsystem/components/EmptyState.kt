package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

/**
 * Empty state component shown when no bookmarks match filters or feed is empty
 *
 * Displays a centered icon, title, message, and optional action button.
 * Used throughout the app for empty bookmark lists, search results, and filtered views.
 *
 * @param title Main heading text (e.g., "No bookmarks yet")
 * @param message Descriptive text below title (e.g., "Start saving content to see it here")
 * @param icon Icon to display above text (default: BookmarkBorder)
 * @param actionText Optional button text (e.g., "Add first bookmark")
 * @param onActionClick Optional callback when action button is clicked
 * @param modifier Modifier to be applied to the component
 */
@Composable
fun EmptyState(
    title: String = "No bookmarks yet",
    message: String = "Start saving content to see it here",
    icon: ImageVector = Icons.Default.BookmarkBorder,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val typography = LocalCrumbsTypography.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = colors.accent.copy(alpha = 0.3f) // Increased visibility
        )

        Spacer(modifier = Modifier.height(spacing.lg))

        Text(
            text = title,
            style = typography.headingMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(spacing.sm))

        Text(
            text = message,
            style = typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )

        // Optional action button
        if (actionText != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(spacing.xl))

            CrumbsButton(
                onClick = onActionClick,
                text = actionText
            )
        }
    }
}

// Previews
@Preview(name = "Default Empty State - Light", showBackground = true)
@Composable
private fun PreviewEmptyStateDefault() {
    CrumbsTheme(darkTheme = false) {
        EmptyState()
    }
}

@Preview(name = "Default Empty State - Dark", showBackground = true)
@Composable
private fun PreviewEmptyStateDark() {
    CrumbsTheme(darkTheme = true) {
        EmptyState()
    }
}

@Preview(name = "Filtered Empty State - Light", showBackground = true)
@Composable
private fun PreviewEmptyStateFiltered() {
    CrumbsTheme(darkTheme = false) {
        EmptyState(
            title = "No results found",
            message = "No bookmarks match your search for 'design patterns'"
        )
    }
}

@Preview(name = "With Action Button - Light", showBackground = true)
@Composable
private fun PreviewEmptyStateWithAction() {
    CrumbsTheme(darkTheme = false) {
        EmptyState(
            title = "No bookmarks yet",
            message = "Start saving content to see it here",
            actionText = "Add first bookmark",
            onActionClick = {}
        )
    }
}

@Preview(name = "With Action Button - Dark", showBackground = true)
@Composable
private fun PreviewEmptyStateWithActionDark() {
    CrumbsTheme(darkTheme = true) {
        EmptyState(
            title = "No bookmarks yet",
            message = "Start saving content to see it here",
            actionText = "Add first bookmark",
            onActionClick = {}
        )
    }
}
