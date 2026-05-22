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

/**
 * Blocking onboarding surface shown when the user is signed in but has not
 * yet linked X. Reuses brutalist primitives (CrumbsButton); no Material3
 * leaks.
 *
 * testTag: `connect-x-screen` — Maestro pivots on this.
 */
@Composable
fun ConnectXOnboardingScreen(
    onConnect: () -> Unit,
    onSkip: () -> Unit,
    connecting: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCrumbsColors.current
    val typography = LocalCrumbsTypography.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .testTag("connect-x-screen"),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "CONNECT X",
            style = typography.displaySmall,
            color = colors.ink,
            modifier = Modifier.testTag("connect-x-kicker"),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Authorize Crumb to read your X bookmarks. Your tokens stay server-side; the app only sees the synced docs.",
            style = typography.bodyMono,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        CrumbsButton(
            onClick = onConnect,
            text = if (connecting) "CONNECTING..." else "CONNECT X",
            enabled = !connecting,
            modifier = Modifier.testTag("connect-x-button"),
        )
        Spacer(Modifier.height(12.dp))
        CrumbsButton(
            onClick = onSkip,
            text = "SKIP FOR NOW",
            style = ButtonStyle.Secondary,
            modifier = Modifier.testTag("connect-x-skip"),
        )
    }
}
