package com.github.jayteealao.crumbs.designsystem.layouts

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsStroke
import kotlin.math.hypot
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke

// Brutalist floating-card overlay shell. In-tree composition (Box +
// AnimatedVisibility + BackHandler) instead of Popup so IME insets dispatch
// correctly and TalkBack can address the backdrop dismiss target.
//
// Surface is center-anchored with 16dp horizontal / 96dp vertical inset.
// Offset shadow uses native Compose Modifier.dropShadow at +6dp/+6dp.
// Backdrop is a 45° hatched scrim (HatchedScrim) at 32% base alpha.
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
            // 45° hatched scrim — flat 32% black + 8dp-spaced diagonal lines drawn
            // inline via Modifier.drawWithCache so the click-target Box stays
            // child-less and its semantics OnClick dispatches cleanly.
            // HatchedScrim composable in components/ is the canonical reference
            // for the same pattern when used outside a click-target boundary.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .drawWithCache {
                        val spacingPx = 8.dp.toPx()
                        val strokePx = 1.dp.toPx()
                        val diagonal = hypot(size.width, size.height)
                        val hatchColor = Color.Black.copy(alpha = 0.08f)
                        onDrawWithContent {
                            drawContent()
                            rotate(degrees = 45f) {
                                var x = -diagonal
                                while (x <= diagonal) {
                                    drawLine(
                                        color = hatchColor,
                                        start = Offset(x, -diagonal),
                                        end = Offset(x, diagonal),
                                        strokeWidth = strokePx,
                                    )
                                    x += spacingPx
                                }
                            }
                        }
                    }
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
            enter = scaleIn(
                initialScale = 0.95f,
                animationSpec = tween(durationMillis = 150, easing = LinearEasing),
            ) + fadeIn(animationSpec = tween(durationMillis = 150)),
            exit = scaleOut(
                targetScale = 0.95f,
                animationSpec = tween(durationMillis = 150, easing = LinearEasing),
            ) + fadeOut(animationSpec = tween(durationMillis = 150)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Surface(
                color = colors.surface,
                contentColor = colors.ink,
                shape = shapes.dialog,
                border = BorderStroke(stroke.regular, colors.ink),
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 96.dp)
                    .dropShadow(
                        shape = shapes.dialog,
                        shadow = Shadow(
                            radius = 0.dp,
                            spread = 0.dp,
                            color = colors.offsetShadow,
                            offset = DpOffset(CrumbsStroke.offsetX, CrumbsStroke.offsetY),
                        ),
                    )
                    .imePadding(),
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
            header = { Text("Filters", modifier = Modifier.fillMaxSize()) },
            footer = { Text("APPLY", modifier = Modifier.fillMaxSize()) },
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
            header = { Text("Filters", modifier = Modifier.fillMaxSize()) },
            footer = { Text("APPLY", modifier = Modifier.fillMaxSize()) },
        ) {
            Text("Overlay body content")
        }
    }
}
