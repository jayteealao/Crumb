package com.github.jayteealao.crumbs.designsystem.layouts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors

// Brutalist home layout shell. Slot-generic Scaffold wrapper that owns:
//   - paper background (LocalCrumbsColors.current.background)
//   - statusBar inset consumed once at the topBar Column (avoids double-consume
//     against Scaffold.contentWindowInsets)
//   - navigationBar inset consumed once at the bottomBar Box
//   - filterBar slot (optional) composed below topBar in the same Column
//
// This is intentionally NOT a wrapper around CrumbsScaffold — that would
// collide the "scaffold-root" testTag with this shell's "home-scaffold" testTag.

@Composable
fun HomeScaffold(
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    filterBar: (@Composable () -> Unit)? = null,
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = LocalCrumbsColors.current
    Scaffold(
        modifier = modifier.testTag("home-scaffold"),
        containerColor = colors.background,
        contentColor = colors.ink,
        contentWindowInsets = contentWindowInsets,
        topBar = {
            Column(
                Modifier
                    .statusBarsPadding()
                    .testTag("home-scaffold-topbar")
            ) {
                topBar()
                if (filterBar != null) {
                    Box(Modifier.testTag("home-scaffold-filterbar")) {
                        filterBar()
                    }
                }
            }
        },
        bottomBar = {
            Box(
                Modifier
                    .navigationBarsPadding()
                    .testTag("home-scaffold-bottombar")
            ) {
                bottomBar()
            }
        },
        content = content,
    )
}

// Previews — render with labeled stub blocks so the layout math is visible
// in Android Studio without dragging in real component dependencies.

@Composable
private fun PreviewStubBlock(label: String, heightDp: Int) {
    val colors = LocalCrumbsColors.current
    Box(
        Modifier
            .fillMaxSize()
            .height(heightDp.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = colors.ink)
    }
}

@Preview(name = "HomeScaffold Light", showBackground = true)
@Composable
private fun PreviewHomeScaffoldLight() {
    CrumbsTheme(darkTheme = false) {
        HomeScaffold(
            topBar = { PreviewStubBlock("topBar", 88) },
            filterBar = { PreviewStubBlock("filterBar", 34) },
            bottomBar = { PreviewStubBlock("bottomBar", 52) },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("preview-content")
            ) {
                Text("content padding=$padding", modifier = Modifier.padding(padding))
            }
        }
    }
}

@Preview(name = "HomeScaffold Dark", showBackground = true)
@Composable
private fun PreviewHomeScaffoldDark() {
    CrumbsTheme(darkTheme = true) {
        HomeScaffold(
            topBar = { PreviewStubBlock("topBar", 88) },
            filterBar = { PreviewStubBlock("filterBar", 34) },
            bottomBar = { PreviewStubBlock("bottomBar", 52) },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("preview-content")
            ) {
                Text("content padding=$padding", modifier = Modifier.padding(padding))
            }
        }
    }
}
