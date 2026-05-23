package com.github.jayteealao.crumbs.designsystem.layouts

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke

// Brutalist bottom-anchored overlay shell. In-tree composition (Box +
// AnimatedVisibility + BackHandler) instead of Popup so IME insets dispatch
// correctly and TalkBack can address the backdrop dismiss target.
//
// Slots: header (optional), body, footer (optional). Footer is tagged
// `overlay-shell-apply` because the most common use is an APPLY button on
// filter overlays. Backdrop tap and back-press both fire `onDismiss`.

@Composable
fun OverlayShell(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    body: @Composable () -> Unit,
) {
    val colors = LocalCrumbsColors.current
    val stroke = LocalCrumbsStroke.current
    val shapes = LocalCrumbsShapes.current
    val backdropInteraction = remember { MutableInteractionSource() }

    Box(
        modifier
            .fillMaxSize()
            .testTag("overlay-shell")
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .semantics { contentDescription = "Dismiss overlay" }
                    .clickable(
                        interactionSource = backdropInteraction,
                        indication = null,
                    ) { onDismiss() }
                    .testTag("overlay-shell-backdrop")
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                color = colors.surface,
                contentColor = colors.ink,
                shape = shapes.dialog,
                border = BorderStroke(stroke.regular, colors.ink),
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding(),
            ) {
                Column {
                    if (header != null) {
                        Box(Modifier.testTag("overlay-shell-header")) { header() }
                    }
                    Box(Modifier.testTag("overlay-shell-body")) { body() }
                    if (footer != null) {
                        Box(Modifier.testTag("overlay-shell-apply")) { footer() }
                    }
                }
            }
        }
    }

    BackHandler(enabled = visible) { onDismiss() }
}

// Previews

@Preview(name = "OverlayShell Open Light", showBackground = true)
@Composable
private fun PreviewOverlayShellLight() {
    CrumbsTheme(darkTheme = false) {
        OverlayShell(
            visible = true,
            onDismiss = {},
            header = { Text("Filters", modifier = Modifier.fillMaxWidth()) },
            footer = { Text("APPLY", modifier = Modifier.fillMaxWidth()) },
        ) {
            Text("Overlay body content")
        }
    }
}

@Preview(name = "OverlayShell Open Dark", showBackground = true)
@Composable
private fun PreviewOverlayShellDark() {
    CrumbsTheme(darkTheme = true) {
        OverlayShell(
            visible = true,
            onDismiss = {},
            header = { Text("Filters", modifier = Modifier.fillMaxWidth()) },
            footer = { Text("APPLY", modifier = Modifier.fillMaxWidth()) },
        ) {
            Text("Overlay body content")
        }
    }
}
