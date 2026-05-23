package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography
import com.github.jayteealao.crumbs.models.Bookmark
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.roundToInt

// Brutalist CrumbsLongPressPopup — anchored long-press contextual menu per
// handoff-components.jsx:608-635. 2×2 grid of action cells with an 8dp
// horizontal+vertical gap, per-cell ink borders (red error border on
// destructive), surface fill on non-primary cells, native Compose dropShadow
// at +6dp/+6dp offset, full-screen scrim. Anchored at a fingertip Offset.

// JS-spec data class (handoff-components.jsx:628-634). No `icon` field — the
// per-cell ImageVector is a deliberate Compose enhancement sourced separately
// (see `actionIcon` below) so the data shape stays byte-stable with JS.
@androidx.compose.runtime.Immutable
data class CrumbsAction(
    val id: String,
    val label: String,
    val hint: String = "",
    val primary: Boolean = false,
    val danger: Boolean = false,
)

// Bundle a list of actions with their dispatcher so call sites stay compact.
@androidx.compose.runtime.Immutable
data class LongPressActions(
    val actions: ImmutableList<CrumbsAction>,
    val onSelect: (CrumbsAction) -> Unit,
)

@Composable
fun CrumbsLongPressPopup(
    visible: Boolean,
    onDismiss: () -> Unit,
    actions: ImmutableList<CrumbsAction>,
    onSelect: (CrumbsAction) -> Unit,
    anchorOffsetPx: Offset = Offset.Zero,
    headerKicker: String? = null,
    headerHandle: String? = null,
    headerAge: String? = null,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val colors = LocalCrumbsColors.current
    val stroke = LocalCrumbsStroke.current
    val shapes = LocalCrumbsShapes.current
    val typography = LocalCrumbsTypography.current
    val spacing = LocalCrumbsSpacing.current

    val positionProvider = remember(anchorOffsetPx) {
        FingertipPopupPositionProvider(anchorOffsetPx)
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = false,
        ),
    ) {
        // Full-screen 32% black scrim — handoff-components.jsx:619.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .testTag("popup-scrim"),
        )
        // Brutalist hard offset shadow via Compose 1.11.x native Modifier.dropShadow —
        // radius=0, spread=0, +6dp/+6dp offset, ink color (handoff-tokens.jsx:273-274).
        Column(
            modifier = modifier
                .dropShadow(
                    shape = shapes.dialog,
                    shadow = Shadow(
                        radius = 0.dp,
                        spread = 0.dp,
                        color = colors.offsetShadow,
                        offset = DpOffset(CrumbsStroke.offsetX, CrumbsStroke.offsetY),
                    ),
                )
                .background(colors.surface)
                .border(stroke.regular, colors.ink, shapes.dialog)
                .testTag("popup"),
        ) {
            if (headerKicker != null || headerHandle != null || headerAge != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(colors.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "·",
                            style = typography.captionMono,
                            color = colors.onAccent,
                        )
                    }
                    if (headerKicker != null) {
                        Text(
                            text = headerKicker.uppercase(),
                            style = typography.captionMono,
                            color = colors.ink,
                        )
                    }
                    if (headerHandle != null) {
                        Text(
                            text = headerHandle,
                            style = typography.bodyMono,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    if (headerAge != null) {
                        Box(
                            modifier = Modifier
                                .width(stroke.hairline)
                                .padding(vertical = 2.dp)
                                .background(colors.ink),
                        )
                        Text(
                            text = headerAge.uppercase(),
                            style = typography.metaMono,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(stroke.hairline)
                        .background(colors.ink),
                )
            }
            // 2×2 grid with 8dp gaps between cells (handoff-components.jsx:614).
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val pairs = actions.chunked(2)
                pairs.forEach { rowActions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowActions.forEach { action ->
                            PopupActionCell(
                                action = action,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics(mergeDescendants = true) {
                                        role = Role.Button
                                        contentDescription = action.label
                                    }
                                    .clickable {
                                        onSelect(action)
                                        onDismiss()
                                    }
                                    .testTag("popup-action-${action.id}"),
                            )
                        }
                        // Pad short final row with an empty weighted spacer to
                        // keep cell widths consistent across rows.
                        if (rowActions.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PopupActionCell(
    action: CrumbsAction,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCrumbsColors.current
    val stroke = LocalCrumbsStroke.current
    val typography = LocalCrumbsTypography.current
    val spacing = LocalCrumbsSpacing.current
    val shapes = LocalCrumbsShapes.current

    val bg = if (action.primary) colors.accent else colors.surface
    val borderColor = if (action.danger) colors.error else colors.ink
    val labelColor = when {
        action.danger -> colors.error
        action.primary -> colors.onAccent
        else -> colors.ink
    }
    val hintColor = if (action.primary) colors.onAccent.copy(alpha = 0.65f) else colors.ink.copy(alpha = 0.65f)
    val icon = actionIcon(action.id)

    Column(
        modifier = modifier
            .background(bg)
            .border(stroke.regular, borderColor, shapes.dialog)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = labelColor,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = action.label.uppercase(),
            style = typography.captionMono,
            color = labelColor,
        )
        if (action.hint.isNotBlank()) {
            Text(
                text = action.hint.uppercase(),
                style = typography.metaMono,
                color = hintColor,
            )
        }
    }
}

// Per-action icon — JS spec carries no icon field on CrumbsAction so the
// mapping lives here as a Compose-only enhancement.
private fun actionIcon(id: String): ImageVector? = when (id) {
    "tag" -> Icons.Default.LocalOffer
    "open" -> Icons.Default.Language
    "share" -> Icons.Default.Share
    "delete" -> Icons.Default.Delete
    else -> null
}

// Anchors the popup near the fingertip Offset (window pixels); clamps to
// window bounds so the popup never overflows the screen.
private class FingertipPopupPositionProvider(
    private val anchorOffsetPx: Offset,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val rawX = anchorBounds.left + anchorOffsetPx.x.roundToInt()
        val rawY = anchorBounds.top + anchorOffsetPx.y.roundToInt()
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(
            x = rawX.coerceIn(0, maxX),
            y = rawY.coerceIn(0, maxY),
        )
    }
}

// ---------------------------------------------------------------------------
// Long-press state holder — shared across all bookmark route composables.
// ---------------------------------------------------------------------------

@Stable
class LongPressState {
    var bookmark by mutableStateOf<Bookmark?>(null)
    var anchor by mutableStateOf(Offset.Zero)
    var showTagEditor by mutableStateOf(false)

    fun dismiss() {
        showTagEditor = false
        bookmark = null
    }
}

@Composable
fun rememberLongPressState(): LongPressState = remember { LongPressState() }

// ---------------------------------------------------------------------------
// Canonical 4-action list — TAG / OPEN / SHARE / DELETE — used by every
// bookmark route. Returns a LongPressActions bundle that keeps the action
// list immutable and the dispatcher closed over the per-route lambdas.
// ---------------------------------------------------------------------------

fun bookmarkPopupActions(
    onTag: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
): LongPressActions {
    val actions = persistentListOf(
        CrumbsAction(id = "tag", label = "Tag", hint = "Add", primary = true),
        CrumbsAction(id = "open", label = "Open", hint = "Url"),
        CrumbsAction(id = "share", label = "Share", hint = "Link"),
        CrumbsAction(id = "delete", label = "Delete", hint = "Remove", danger = true),
    )
    val onSelect: (CrumbsAction) -> Unit = { action ->
        when (action.id) {
            "tag" -> onTag()
            "open" -> onOpen()
            "share" -> onShare()
            "delete" -> onDelete()
        }
    }
    return LongPressActions(actions, onSelect)
}

// Default 4-action set matching handoff Screen 5 — TAG / SHARE / ARCHIVE /
// DELETE (kept for design-system previews + legacy callers).
fun defaultPopupActions(
    onTag: () -> Unit = {},
    onShare: () -> Unit = {},
    onArchive: () -> Unit = {},
    onDelete: () -> Unit = {},
): LongPressActions {
    val actions = persistentListOf(
        CrumbsAction(id = "tag", label = "Tag", hint = "Add", primary = true),
        CrumbsAction(id = "share", label = "Share", hint = "Link"),
        CrumbsAction(id = "archive", label = "Archive", hint = "Hide"),
        CrumbsAction(id = "delete", label = "Delete", hint = "Remove", danger = true),
    )
    val onSelect: (CrumbsAction) -> Unit = { action ->
        when (action.id) {
            "tag" -> onTag()
            "share" -> onShare()
            "archive" -> onArchive()
            "delete" -> onDelete()
        }
    }
    return LongPressActions(actions, onSelect)
}

@Preview(name = "Default Light", showBackground = true)
@Composable
private fun PreviewLongPressPopupLight() {
    CrumbsTheme(darkTheme = false) {
        val bundle = defaultPopupActions()
        CrumbsLongPressPopup(
            visible = true,
            onDismiss = {},
            actions = bundle.actions,
            onSelect = bundle.onSelect,
            headerKicker = "Twitter",
            headerHandle = "@designpatterns",
            headerAge = "1h",
        )
    }
}

@Preview(name = "Default Dark", showBackground = true)
@Composable
private fun PreviewLongPressPopupDark() {
    CrumbsTheme(darkTheme = true) {
        val bundle = defaultPopupActions()
        CrumbsLongPressPopup(
            visible = true,
            onDismiss = {},
            actions = bundle.actions,
            onSelect = bundle.onSelect,
            headerKicker = "Reddit",
            headerHandle = "u/androiddev",
            headerAge = "2d",
        )
    }
}
