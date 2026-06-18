package com.github.jayteealao.crumbs.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jayteealao.crumbs.designsystem.components.ButtonStyle
import com.github.jayteealao.crumbs.designsystem.components.CrumbsButton
import com.github.jayteealao.crumbs.designsystem.layouts.OverlayShell
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

/**
 * Full-screen entry point for the account-deletion flow.
 *
 * Shows a single "DELETE ACCOUNT" action with a double-confirmation dialog.
 * On server confirmation the composable invokes [onAccountDeleted] so the
 * caller can navigate to the login screen and clear the back-stack.
 *
 * Wiring into navigation (follow-up task): add a `Screens.DELETE_ACCOUNT`
 * entry to `Crumbs.kt` / the `Screens` enum, and link to it from
 * [SettingsScreen] (or the caller's preferred entry point).
 *
 * @param onAccountDeleted Called after the server confirms erasure and local
 *   sign-out completes. Navigate to the login / onboarding destination here.
 */
@Composable
fun DeleteAccountRoute(
    onAccountDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeleteAccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is DeleteAccountState.Done) {
            onAccountDeleted()
        }
    }

    DeleteAccountScreen(
        isInProgress = state is DeleteAccountState.InProgress,
        errorMessage = (state as? DeleteAccountState.Error)?.reason,
        onConfirmDelete = { viewModel.deleteAccount() },
        onErrorDismiss = { viewModel.resetError() },
        modifier = modifier,
    )
}

@Composable
fun DeleteAccountScreen(
    isInProgress: Boolean,
    errorMessage: String?,
    onConfirmDelete: () -> Unit,
    onErrorDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCrumbsColors.current
    val typography = LocalCrumbsTypography.current
    val spacing = LocalCrumbsSpacing.current
    val stroke = LocalCrumbsStroke.current

    var showConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = spacing.xl, vertical = spacing.xl)
            .testTag("delete-account-screen"),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "ACCOUNT",
            style = typography.displaySmall,
            color = colors.ink,
        )
        Spacer(Modifier.height(spacing.xl))

        Text(
            text = "DATA ERASURE",
            style = typography.captionMono,
            color = colors.ink,
        )
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = "Permanently deletes your account, all saved bookmarks, " +
                "and your linked X credentials from this service.",
            style = typography.bodyMono,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.lg))

        if (isInProgress) {
            CircularProgressIndicator(
                color = colors.ink,
                modifier = Modifier.testTag("delete-account-progress"),
            )
        } else {
            CrumbsButton(
                onClick = { showConfirm = true },
                text = "DELETE ACCOUNT",
                style = ButtonStyle.Primary,
                modifier = Modifier.testTag("delete-account-open-dialog"),
            )
        }
    }

    // ── Confirmation dialog ───────────────────────────────────────────────
    OverlayShell(
        visible = showConfirm && !isInProgress,
        onDismiss = { showConfirm = false },
        modifier = Modifier.testTag("delete-account-overlay"),
        body = {
            Column(
                modifier = Modifier
                    .background(colors.surface)
                    .border(BorderStroke(stroke.regular, colors.ink))
                    .padding(spacing.xl)
                    .testTag("delete-account-confirm-dialog"),
            ) {
                Text(
                    text = "DELETE ACCOUNT",
                    style = typography.displaySmall,
                    color = colors.error,
                )
                Spacer(Modifier.height(spacing.sm))
                Text(
                    text = "This cannot be undone. All bookmarks and your account " +
                        "will be permanently erased.",
                    style = typography.bodyMono,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(spacing.xl))
                Row(modifier = Modifier.fillMaxWidth()) {
                    CrumbsButton(
                        onClick = { showConfirm = false },
                        text = "CANCEL",
                        style = ButtonStyle.Secondary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("delete-account-confirm-cancel"),
                    )
                    Spacer(Modifier.width(spacing.md))
                    CrumbsButton(
                        onClick = {
                            showConfirm = false
                            onConfirmDelete()
                        },
                        text = "DELETE",
                        style = ButtonStyle.Primary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("delete-account-confirm-yes"),
                    )
                }
            }
        },
    )

    // ── Error dialog ──────────────────────────────────────────────────────
    OverlayShell(
        visible = errorMessage != null,
        onDismiss = onErrorDismiss,
        modifier = Modifier.testTag("delete-account-error-overlay"),
        body = {
            Column(
                modifier = Modifier
                    .background(colors.surface)
                    .border(BorderStroke(stroke.regular, colors.error))
                    .padding(spacing.xl)
                    .testTag("delete-account-error-dialog"),
            ) {
                Text(
                    text = "ERROR",
                    style = typography.displaySmall,
                    color = colors.error,
                )
                Spacer(Modifier.height(spacing.sm))
                Text(
                    text = "Account deletion failed. Please try again or contact support.",
                    style = typography.bodyMono,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(spacing.xl))
                CrumbsButton(
                    onClick = onErrorDismiss,
                    text = "DISMISS",
                    style = ButtonStyle.Secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("delete-account-error-dismiss"),
                )
            }
        },
    )
}
