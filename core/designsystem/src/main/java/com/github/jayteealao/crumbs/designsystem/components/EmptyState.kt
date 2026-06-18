package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

// Brutalist EmptyState — typographic, no icon (icons soften brutalist register).
// Funnel Display title + mono kicker subtitle + optional accent CTA via
// CrumbsButton. Lives in a fillMaxSize centered Column.

@Composable
fun EmptyState(
    title: String = "No bookmarks yet",
    message: String = "Start saving content to see it here",
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
            .padding(spacing.xxl)
            .testTag("empty-state"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = typography.displayHeadline,
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(spacing.sm))
        Text(
            text = message.uppercase(),
            style = typography.captionMono,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(spacing.xl))
            CrumbsButton(
                onClick = onActionClick,
                text = actionText,
                modifier = Modifier.testTag("empty-state-cta"),
            )
        }
    }
}

// Previews
@Preview(name = "Default Light", showBackground = true)
@Composable
private fun PreviewEmptyStateDefault() {
    CrumbsTheme(darkTheme = false) { EmptyState() }
}

@Preview(name = "Default Dark", showBackground = true)
@Composable
private fun PreviewEmptyStateDark() {
    CrumbsTheme(darkTheme = true) { EmptyState() }
}

@Preview(name = "With Action Light", showBackground = true)
@Composable
private fun PreviewEmptyStateWithAction() {
    CrumbsTheme(darkTheme = false) {
        EmptyState(
            title = "No bookmarks yet",
            message = "Start saving content to see it here",
            actionText = "Add first bookmark",
            onActionClick = {},
        )
    }
}
