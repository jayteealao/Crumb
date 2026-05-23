package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import com.github.jayteealao.crumbs.designsystem.layouts.OverlayShell
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography
import com.github.jayteealao.crumbs.models.Bookmark
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Screen-level bookmark long-press overlay. Wraps the existing
 * [CrumbsLongPressPopup] inside an [OverlayShell] so the action menu inherits
 * the brutalist offset shadow + hatched scrim chrome, then layers the
 * `CURRENT TAGS:` row + plain `+ add tag…` text-link affordance underneath
 * per option-d-screens.jsx DQuickActions lines 664-759.
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
                CrumbsLongPressPopup(
                    visible = true,
                    onDismiss = onDismiss,
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
                    anchorOffsetPx = Offset.Zero,
                    headerKicker = "ACTIONS",
                    headerHandle = bookmark?.author,
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
