package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.layouts.OverlayShell
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography
import com.github.jayteealao.crumbs.models.Bookmark
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Screen-level bookmark long-press overlay. Renders the action cells inline in
 * a single [OverlayShell] (one offset shadow + one hatched scrim), then layers
 * the `CURRENT TAGS:` row + plain `+ add tag…` text-link affordance underneath
 * per option-d-screens.jsx DQuickActions lines 664-759. The cells reuse the
 * [PopupActionCell] primitive but are drawn directly in the shell body — the
 * overlay no longer mounts a nested [CrumbsLongPressPopup] (which opened a
 * second Popup window with its own scrim + back handler).
 *
 * Action set preserved as TAG / OPEN / SHARE / DELETE (the behaviors-slice
 * canonical set). The handoff spec calls for TAG/SHARE/ARCHIVE/DELETE, but
 * the existing wiring, Maestro `popup-action-open` selectors, and the lack of
 * an Archive repository operation make the swap a follow-up decision —
 * resolved at implement-time by the PO to preserve the shipped set.
 *
 * Tapping TAG or the "+ add tag…" link opens the embedded [TagEditorDialog].
 * The OPEN / SHARE / DELETE actions forward to [onActionSelect] and dismiss
 * the overlay so the caller can fire the side effect on its own dispatcher.
 *
 * Lives in `core/designsystem` rather than `app` (plan original location) so
 * the per-tab feed routes in `feature/twitter` and `feature/reddit` can
 * import it without inverting the module-dependency direction.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun BookmarkActionsOverlay(
    visible: Boolean,
    bookmark: Bookmark?,
    currentTags: ImmutableList<String>,
    availableTags: ImmutableList<String>,
    onDismiss: () -> Unit,
    onActionSelect: (CrumbsAction) -> Unit,
    onTagsSave: (ImmutableList<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tagEditorVisible by remember { mutableStateOf(false) }
    val colors = LocalCrumbsColors.current
    val typography = LocalCrumbsTypography.current
    val spacing = LocalCrumbsSpacing.current
    val stroke = LocalCrumbsStroke.current

    // Bug 3 fix: when the active bookmark is cleared externally (the route calls
    // LongPressState.dismiss(), which nulls the bookmark), collapse the tag
    // editor so a later long-press on a *different* bookmark never reopens it
    // unprompted.
    LaunchedEffect(bookmark) {
        if (bookmark == null) tagEditorVisible = false
    }

    OverlayShell(
        visible = visible && bookmark != null,
        onDismiss = onDismiss,
        modifier = modifier,
        body = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.md)
                    .testTag("bookmark-actions-overlay"),
            ) {
                // Bug 2 fix: render the action cells inline instead of mounting a
                // nested CrumbsLongPressPopup, which opened a *second* Popup window
                // with its own scrim + back handler (double-darkening, and Back
                // tearing down the whole overlay). The "ACTIONS · @handle" header
                // is replicated here since the inline cells no longer carry the
                // popup card's own header. testTags and the TAG/OPEN/SHARE/DELETE
                // action set are preserved verbatim for the Maestro selectors.
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                    Text(
                        text = "ACTIONS",
                        style = typography.captionMono,
                        color = colors.ink,
                    )
                    bookmark?.author?.let { author ->
                        Text(
                            text = author,
                            style = typography.bodyMono,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(spacing.sm))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(stroke.hairline)
                        .background(colors.ink),
                )
                Spacer(Modifier.height(spacing.sm))
                BookmarkActionGrid(
                    actions = POPUP_ACTION_TEMPLATE,
                    onSelect = { action ->
                        when (action.id) {
                            "tag" -> tagEditorVisible = true
                            else -> {
                                onActionSelect(action)
                                onDismiss()
                            }
                        }
                    },
                )
                Spacer(Modifier.height(spacing.md))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bookmark-actions-tag-row"),
                ) {
                    Text(
                        text = "CURRENT TAGS:",
                        style = typography.captionMono,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(spacing.xs))
                    if (currentTags.isEmpty()) {
                        Text(
                            text = "—",
                            style = typography.bodyMono,
                            color = colors.onSurfaceVariant,
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            verticalArrangement = Arrangement.spacedBy(spacing.xs),
                        ) {
                            currentTags.forEach { tag ->
                                CrumbsTagChip(label = tag, onClick = {})
                            }
                        }
                    }
                    Spacer(Modifier.height(spacing.sm))
                    Text(
                        text = "+ add tag…",
                        style = typography.captionMono,
                        color = colors.accent,
                        modifier = Modifier
                            .clickable { tagEditorVisible = true }
                            .testTag("bookmark-actions-add-tag"),
                    )
                }
            }
        },
    )

    TagEditorDialog(
        isVisible = tagEditorVisible && bookmark != null,
        currentTags = currentTags,
        availableTags = availableTags,
        onDismiss = { tagEditorVisible = false },
        onSave = { saved ->
            onTagsSave(saved)
            tagEditorVisible = false
            onDismiss()
        },
    )
}

/**
 * Inline 2×2 action grid — the un-nested replacement for the action cells that
 * [CrumbsLongPressPopup] used to draw inside its own Popup window. Reuses the
 * [PopupActionCell] primitive (promoted to `internal`) so the cell visuals stay
 * byte-identical, and preserves the `popup-action-<id>` testTag + button
 * semantics on every cell. TAG routes to the tag editor via [onSelect]; the
 * dismiss for the other actions is owned by the caller's [onSelect] lambda.
 */
@Composable
private fun BookmarkActionGrid(
    actions: ImmutableList<CrumbsAction>,
    onSelect: (CrumbsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actions.chunked(2).forEach { rowActions ->
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
                            .clickable { onSelect(action) }
                            .testTag("popup-action-${action.id}"),
                    )
                }
                // Pad a short final row with a weighted spacer so cell widths
                // stay consistent across rows.
                if (rowActions.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Pre-instantiated action template — id/label/primary/danger metadata is
 * static so the bundle does not need to be rebuilt on each composition. The
 * id-keyed dispatcher above routes TAG locally to the tag editor and all
 * other ids out to [onActionSelect].
 */
private val POPUP_ACTION_TEMPLATE: kotlinx.collections.immutable.ImmutableList<CrumbsAction> =
    persistentListOf(
        CrumbsAction(id = "tag", label = "Tag", hint = "Add", primary = true),
        CrumbsAction(id = "open", label = "Open", hint = "Url"),
        CrumbsAction(id = "share", label = "Share", hint = "Link"),
        CrumbsAction(id = "delete", label = "Delete", hint = "Remove", danger = true),
    )
