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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.github.jayteealao.crumbs.designsystem.components.ButtonStyle
import com.github.jayteealao.crumbs.designsystem.components.CrumbsButton
import com.github.jayteealao.crumbs.designsystem.layouts.OverlayShell
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography
import com.github.jayteealao.twitter.data.dto.SyncStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Displays app configuration options, currently focused on the X (Twitter) sync connection.
 * Shows the live sync-status (connected / disconnected, last-polled timestamp, and any error),
 * and offers a "Disconnect X" action protected by a confirmation dialog.
 *
 * @param syncStatus Server-reported sync state, or `null` while loading; drives the connection
 *   status label, last-polled timestamp, and error row.
 * @param onDisconnectClick Called after the user confirms the disconnect action in the dialog.
 */
@Composable
fun SettingsScreen(
    syncStatus: SyncStatus?,
    onDisconnectClick: () -> Unit,
    onDeleteAccountClick: () -> Unit = {},
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
            .testTag("settings-screen"),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "SETTINGS",
            style = typography.displaySmall,
            color = colors.ink,
        )
        Spacer(Modifier.height(spacing.xl))

        // ── X sync-status row ──────────────────────────────────────────────
        Text(
            text = "X SYNC",
            style = typography.captionMono,
            color = colors.ink,
            modifier = Modifier.testTag("settings-x-kicker"),
        )
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = if (syncStatus?.linked == true) "CONNECTED" else "DISCONNECTED",
            style = typography.bodyMono,
            color = if (syncStatus?.linked == true) colors.success else colors.error,
            modifier = Modifier
                .testTag("settings-x-state")
                .semantics {
                    stateDescription = if (syncStatus?.linked == true) {
                        "X sync enabled"
                    } else {
                        "X sync disabled"
                    }
                },
        )
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = "Last polled: ${formatTimestamp(syncStatus?.lastPolledAt?.toDate())}",
            style = typography.metaMono,
            color = colors.onSurfaceVariant,
            modifier = Modifier.testTag("settings-x-last-polled"),
        )
        val err = syncStatus?.lastError
        if (!err.isNullOrBlank()) {
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = "ERROR · ${err.uppercase()}",
                style = typography.captionMono,
                color = colors.error,
                modifier = Modifier.testTag("settings-x-error"),
            )
        }
        Spacer(Modifier.height(spacing.lg))
        CrumbsButton(
            onClick = { showConfirm = true },
            text = "DISCONNECT X",
            style = ButtonStyle.Secondary,
            modifier = Modifier.testTag("settings-disconnect-x"),
        )

        Spacer(Modifier.height(spacing.xl))

        // ── Account / data erasure ─────────────────────────────────────────
        Text(
            text = "ACCOUNT",
            style = typography.captionMono,
            color = colors.ink,
            modifier = Modifier.testTag("settings-account-kicker"),
        )
        Spacer(Modifier.height(spacing.lg))
        CrumbsButton(
            onClick = onDeleteAccountClick,
            text = "DELETE ACCOUNT",
            style = ButtonStyle.Secondary,
            modifier = Modifier.testTag("settings-delete-account"),
        )
    }

    OverlayShell(
        visible = showConfirm,
        onDismiss = { showConfirm = false },
        modifier = Modifier.testTag("settings-disconnect-overlay"),
        body = {
            Column(
                modifier = Modifier
                    .background(colors.surface)
                    .border(BorderStroke(stroke.regular, colors.ink))
                    .padding(spacing.xl)
                    .testTag("settings-disconnect-confirm-dialog"),
            ) {
                Text(
                    text = "DISCONNECT X",
                    style = typography.displaySmall,
                    color = colors.ink,
                )
                Spacer(Modifier.height(spacing.sm))
                Text(
                    text = "Your bookmarks stay on this device.",
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
                            .testTag("settings-disconnect-confirm-cancel"),
                    )
                    Spacer(Modifier.width(spacing.md))
                    CrumbsButton(
                        onClick = {
                            showConfirm = false
                            onDisconnectClick()
                        },
                        text = "DISCONNECT",
                        style = ButtonStyle.Primary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings-disconnect-confirm-yes"),
                    )
                }
            }
        },
    )
}

private val timestampFormat: SimpleDateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}

private fun formatTimestamp(date: Date?): String =
    if (date == null) "Never" else timestampFormat.format(date)
