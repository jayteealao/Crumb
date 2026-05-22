package com.github.jayteealao.crumbs.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.components.ButtonStyle
import com.github.jayteealao.crumbs.designsystem.components.CrumbsButton
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography
import com.github.jayteealao.twitter.data.dto.SyncStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Stateless brutalist Settings screen. The only live data is the X sync-status
 * row; everything else (disconnect, etc.) is a placeholder until
 * cutover-migration ships the real callable.
 *
 * testTag: `settings-screen`.
 */
@Composable
fun SettingsScreen(
    syncStatus: SyncStatus?,
    onDisconnectClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCrumbsColors.current
    val typography = LocalCrumbsTypography.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .testTag("settings-screen"),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "SETTINGS",
            style = typography.displaySmall,
            color = colors.ink,
        )
        Spacer(Modifier.height(24.dp))

        // ── X sync-status row ──────────────────────────────────────────────
        Text(
            text = "X SYNC",
            style = typography.captionMono,
            color = colors.ink,
            modifier = Modifier.testTag("settings-x-kicker"),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (syncStatus?.linked == true) "CONNECTED" else "DISCONNECTED",
            style = typography.bodyMono,
            color = if (syncStatus?.linked == true) colors.success else colors.error,
            modifier = Modifier.testTag("settings-x-state"),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Last polled: ${formatTimestamp(syncStatus?.lastPolledAt?.toDate())}",
            style = typography.metaMono,
            color = colors.onSurfaceVariant,
            modifier = Modifier.testTag("settings-x-last-polled"),
        )
        val err = syncStatus?.lastError
        if (!err.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "ERROR · ${err.uppercase()}",
                style = typography.captionMono,
                color = colors.error,
                modifier = Modifier.testTag("settings-x-error"),
            )
        }
        Spacer(Modifier.height(16.dp))
        CrumbsButton(
            onClick = onDisconnectClick,
            text = "DISCONNECT X",
            style = ButtonStyle.Secondary,
            modifier = Modifier.testTag("settings-disconnect-x"),
        )
    }
}

private val timestampFormat: SimpleDateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}

private fun formatTimestamp(date: Date?): String =
    if (date == null) "Never" else timestampFormat.format(date)
