package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

// Brutalist CrumbsBanner — sticky horizontal banner with 1.5dp ink top + bottom
// border (no side borders — visually anchors to the feed). Surface bg.
// Left: kicker + detail (mono). Right: accent CTA text. Stateless visual.

@Composable
fun CrumbsBanner(
    kickerLine: String,
    detail: String,
    modifier: Modifier = Modifier,
    ctaLabel: String? = null,
    onCta: (() -> Unit)? = null
) {
    val colors = LocalCrumbsColors.current
    val stroke = LocalCrumbsStroke.current
    val typography = LocalCrumbsTypography.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .testTag("banner"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stroke.regular)
                .background(colors.ink),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = kickerLine.uppercase(),
                    style = typography.captionMono,
                    color = colors.ink,
                )
                Text(
                    text = detail,
                    style = typography.metaMono,
                    color = colors.onSurfaceVariant,
                )
            }
            if (ctaLabel != null && onCta != null) {
                Text(
                    text = ctaLabel.uppercase(),
                    style = typography.captionMono,
                    color = colors.accent,
                    modifier = Modifier
                        .clickable { onCta() }
                        .padding(horizontal = 4.dp)
                        .testTag("banner-cta"),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stroke.regular)
                .background(colors.ink),
        )
    }
}

@Preview(name = "Sync Error Light", showBackground = true)
@Composable
private fun PreviewBannerSyncErrorLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsBanner(
            kickerLine = "↳ Sync error",
            detail = "Twitter session expired",
            ctaLabel = "Reconnect",
            onCta = {},
        )
    }
}

@Preview(name = "Success Dark", showBackground = true)
@Composable
private fun PreviewBannerSuccessDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsBanner(
            kickerLine = "↳ Synced",
            detail = "212 bookmarks updated",
        )
    }
}
