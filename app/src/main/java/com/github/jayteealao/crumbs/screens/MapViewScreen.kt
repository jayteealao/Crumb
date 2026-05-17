package com.github.jayteealao.crumbs.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsStroke
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

@Composable
fun MapViewScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier,
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current
    val stroke = LocalCrumbsStroke.current
    val typography = LocalCrumbsTypography.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(contentPadding)
            .padding(horizontal = spacing.xl)
            .testTag("map-view-screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "MAP",
            style = typography.captionMono,
            color = colors.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(spacing.md))
        Text(
            text = "COMING SOON",
            style = typography.displaySmall,
            color = colors.ink,
            modifier = Modifier.testTag("map-view-coming-soon"),
        )
        Spacer(modifier = Modifier.height(spacing.xl))
        Box(
            modifier = Modifier
                .size(width = 240.dp, height = 160.dp)
                .border(stroke.regular, colors.ink, RectangleShape)
                .background(colors.surface),
        )
    }
}

@Composable
fun MapViewRoute(contentPadding: PaddingValues) {
    MapViewScreen(contentPadding = contentPadding)
}

@Preview(name = "MapView Light", showBackground = true)
@Composable
private fun PreviewMapViewLight() {
    CrumbsTheme(darkTheme = false) { MapViewScreen() }
}

@Preview(name = "MapView Dark", showBackground = true)
@Composable
private fun PreviewMapViewDark() {
    CrumbsTheme(darkTheme = true) { MapViewScreen() }
}
