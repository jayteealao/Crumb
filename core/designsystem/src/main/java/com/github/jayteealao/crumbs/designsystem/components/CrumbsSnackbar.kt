package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

// Brutalist CrumbsSnackbar — stateless visual shell. Ink background, accent
// 1.5dp border. Optional accent-text action button on the right. Timer / show
// / dismiss are caller responsibilities. LiveRegionMode.Polite makes TalkBack
// announce the message without interrupting in-progress narration.

@Composable
fun CrumbsSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = LocalCrumbsColors.current
    val stroke = LocalCrumbsStroke.current
    val shapes = LocalCrumbsShapes.current
    val typography = LocalCrumbsTypography.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(colors.ink)
            .border(stroke.regular, colors.accent, shapes.dialog)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .testTag("snackbar")
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = message,
            style = typography.bodyMono,
            color = colors.surface,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            Text(
                text = actionLabel.uppercase(),
                style = typography.captionMono,
                color = colors.accent,
                modifier = Modifier
                    .semantics(mergeDescendants = true) {
                        contentDescription = actionLabel
                    }
                    .clickable(role = Role.Button) { onAction() }
                    .testTag("snackbar-action"),
            )
        }
    }
}

@Preview(name = "With Action Light", showBackground = true)
@Composable
private fun PreviewSnackbarWithActionLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsSnackbar(message = "Bookmark deleted", actionLabel = "Undo", onAction = {})
    }
}

@Preview(name = "Without Action Dark", showBackground = true)
@Composable
private fun PreviewSnackbarWithoutActionDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsSnackbar(message = "Sync complete")
    }
}
