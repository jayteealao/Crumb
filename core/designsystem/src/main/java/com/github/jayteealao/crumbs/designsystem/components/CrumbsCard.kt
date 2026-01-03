package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing

/**
 * Crumbs card with bottom-end cut corner
 *
 * Component variants for testing:
 * - With/without content
 * - Small/normal size
 * - Light/dark theme
 */

enum class CardSize {
    Small, Normal
}

@Composable
fun CrumbsCard(
    modifier: Modifier = Modifier,
    size: CardSize = CardSize.Normal,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current

    val shape = when (size) {
        CardSize.Small -> CrumbsShapes.cardSmall
        CardSize.Normal -> CrumbsShapes.card
    }

    val contentPadding = when (size) {
        CardSize.Small -> spacing.md
        CardSize.Normal -> spacing.lg
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = colors.surface,
        contentColor = colors.textPrimary,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(contentPadding)
        ) {
            content()
        }
    }
}

// Previews
@Preview(name = "Normal Card Light", showBackground = true)
@Composable
private fun PreviewCardNormalLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Card Title")
            Text("Card content goes here")
        }
    }
}

@Preview(name = "Normal Card Dark", showBackground = true)
@Composable
private fun PreviewCardNormalDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Card Title")
            Text("Card content goes here")
        }
    }
}

@Preview(name = "Small Card", showBackground = true)
@Composable
private fun PreviewCardSmall() {
    CrumbsTheme(darkTheme = false) {
        CrumbsCard(
            size = CardSize.Small
        ) {
            Text("Small card")
        }
    }
}
