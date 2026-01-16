package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors

/**
 * Crumbs scaffold - Material 3 Scaffold wrapper with Crumbs theming
 *
 * Features:
 * - Theme-aware default colors (background and content color)
 * - Standard Material 3 Scaffold functionality
 * - Integrates seamlessly with CrumbsTopBar and CrumbsBottomNav
 * - Handles safe area padding automatically
 *
 * Component variants for testing:
 * - With/without topBar
 * - With/without bottomBar
 * - With/without floatingActionButton
 * - Light/Dark themes
 */

@Composable
fun CrumbsScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable (() -> Unit) = {},
    bottomBar: @Composable (() -> Unit) = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    containerColor: Color = LocalCrumbsColors.current.background,
    contentColor: Color = LocalCrumbsColors.current.textPrimary,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        containerColor = containerColor,
        contentColor = contentColor,
        content = content
    )
}

// Previews
@Preview(name = "Empty Light", showBackground = true)
@Composable
private fun PreviewScaffoldEmptyLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsScaffold {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Content")
            }
        }
    }
}

@Preview(name = "Empty Dark", showBackground = true)
@Composable
private fun PreviewScaffoldEmptyDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsScaffold {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Content")
            }
        }
    }
}

@Preview(name = "With TopBar Light", showBackground = true)
@Composable
private fun PreviewScaffoldWithTopBarLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsScaffold(
            topBar = {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text("Top Bar")
                }
            }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Content with Top Bar")
            }
        }
    }
}

@Preview(name = "With Bottom Bar Dark", showBackground = true)
@Composable
private fun PreviewScaffoldWithBottomBarDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsScaffold(
            bottomBar = {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text("Bottom Bar")
                }
            }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Content with Bottom Bar")
            }
        }
    }
}
