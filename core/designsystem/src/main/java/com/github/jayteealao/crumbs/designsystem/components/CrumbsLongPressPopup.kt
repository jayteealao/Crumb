package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
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

// Brutalist CrumbsLongPressPopup — anchored long-press contextual menu
// matching handoff Screen 5. A 2×2 grid of actions (TAG / SHARE / ARCHIVE /
// DELETE) over an optional source-header row. Anchored at a fingertip Offset
// (window-relative pixels). Container: surface bg, 1.5dp ink border, 6dp ink
// offset shadow.
//
// State-machine wiring (visibility toggle, fingertip Offset capture, action
// dispatch including ARCHIVE behavior) is owned by the behaviors slice; this
// component ships the visual shell only.

@androidx.compose.runtime.Immutable
data class PopupAction(
    val id: String,
    val label: String,
    val hint: String = "",
    val icon: ImageVector? = null,
    val isPrimary: Boolean = false,
    val isDanger: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun CrumbsLongPressPopup(
    visible: Boolean,
    onDismiss: () -> Unit,
    actions: ImmutableList<PopupAction>,
    anchorOffsetPx: Offset = Offset.Zero,
    headerKicker: String? = null,
    headerHandle: String? = null,
    headerAge: String? = null,
    modifier: Modifier = Modifier
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
            clippingEnabled = true,
        ),
    ) {
        Column(
            modifier = modifier
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
                        .background(colors.ink),
                )
            }
            // 2x2 grid: top row (2 cells), bottom row (2 cells)
            val pairs = actions.chunked(2)
            pairs.forEachIndexed { rowIndex, rowActions ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowActions.forEachIndexed { colIndex, action ->
                        PopupActionCell(
                            action = action,
                            modifier = Modifier
                                .weight(1f)
                                // Mixed-case label as a single semantic node so
                                // TalkBack pronounces "Tag" / "Open" instead of
                                // spelling the uppercase glyphs T-A-G letter by
                                // letter. The visual still uppercases inside.
                                .semantics(mergeDescendants = true) {
                                    role = Role.Button
                                    contentDescription = action.label
                                }
                                .clickable {
                                    action.onClick()
                                    onDismiss()
                                }
                                .testTag("popup-action-${action.id}"),
                        )
                        if (colIndex < rowActions.size - 1) {
                            Box(
                                modifier = Modifier
                                    .width(stroke.hairline)
                                    .background(colors.ink),
                            )
                        }
                    }
                }
                if (rowIndex < pairs.size - 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.ink),
                    )
                }
            }
        }
    }
}

@Composable
private fun PopupActionCell(
    action: PopupAction,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCrumbsColors.current
    val typography = LocalCrumbsTypography.current
    val spacing = LocalCrumbsSpacing.current

    val bg = if (action.isPrimary) colors.accent else Color.Transparent
    val labelColor = when {
        action.isDanger -> colors.error
        action.isPrimary -> colors.onAccent
        else -> colors.ink
    }
    val hintColor = if (action.isPrimary) colors.onAccent.copy(alpha = 0.65f) else colors.ink.copy(alpha = 0.65f)

    Column(
        modifier = modifier
            .background(bg)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        action.icon?.let { iv ->
            Icon(
                imageVector = iv,
                // Icon is decorative — the clickable cell above merges a
                // contentDescription on the action, so a duplicate label here
                // would cause TalkBack to read the action name twice.
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

/** Holds the three pieces of state that every bookmark route needs for the
 *  long-press popup: which bookmark was pressed, where on screen, and whether
 *  the tag editor is open. Call [dismiss] to close both the popup and the
 *  tag editor at once.
 */
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
// bookmark route.  The caller provides lambdas so per-route intent logic
// stays in the route composable.
// ---------------------------------------------------------------------------

fun bookmarkPopupActions(
    onTag: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
): ImmutableList<PopupAction> = persistentListOf(
    PopupAction(
        id = "tag",
        label = "Tag",
        hint = "Add",
        icon = Icons.Default.LocalOffer,
        isPrimary = true,
        onClick = onTag,
    ),
    PopupAction(
        id = "open",
        label = "Open",
        hint = "Url",
        icon = Icons.Default.Language,
        onClick = onOpen,
    ),
    PopupAction(
        id = "share",
        label = "Share",
        hint = "Link",
        icon = Icons.Default.Share,
        onClick = onShare,
    ),
    PopupAction(
        id = "delete",
        label = "Delete",
        hint = "Remove",
        icon = Icons.Default.Delete,
        isDanger = true,
        onClick = onDelete,
    ),
)

// Default 4-action set matching handoff Screen 5.
fun defaultPopupActions(
    onTag: () -> Unit = {},
    onShare: () -> Unit = {},
    onArchive: () -> Unit = {},
    onDelete: () -> Unit = {},
): ImmutableList<PopupAction> = persistentListOf(
    PopupAction(
        id = "tag",
        label = "Tag",
        hint = "Add",
        icon = Icons.Default.LocalOffer,
        isPrimary = true,
        onClick = onTag,
    ),
    PopupAction(
        id = "share",
        label = "Share",
        hint = "Link",
        icon = Icons.Default.Share,
        onClick = onShare,
    ),
    PopupAction(
        id = "archive",
        label = "Archive",
        hint = "Hide",
        icon = Icons.Default.Archive,
        onClick = onArchive,
    ),
    PopupAction(
        id = "delete",
        label = "Delete",
        hint = "Remove",
        icon = Icons.Default.Delete,
        isDanger = true,
        onClick = onDelete,
    ),
)

@Preview(name = "Default Light", showBackground = true)
@Composable
private fun PreviewLongPressPopupLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsLongPressPopup(
            visible = true,
            onDismiss = {},
            actions = defaultPopupActions(),
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
        CrumbsLongPressPopup(
            visible = true,
            onDismiss = {},
            actions = defaultPopupActions(),
            headerKicker = "Reddit",
            headerHandle = "u/androiddev",
            headerAge = "2d",
        )
    }
}
