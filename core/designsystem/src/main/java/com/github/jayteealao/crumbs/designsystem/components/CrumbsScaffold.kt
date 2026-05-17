package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors

// Brutalist CrumbsScaffold — kept as a Material3 Scaffold passthrough.
// Pure structural wrapper, no chrome to leak. Defaults pulled from brutalist
// token surface (paper background, ink content color). testTag on root for
// Maestro flow addressing.

@Composable
fun CrumbsScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable (() -> Unit) = {},
    bottomBar: @Composable (() -> Unit) = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    containerColor: Color = LocalCrumbsColors.current.background,
    contentColor: Color = LocalCrumbsColors.current.ink,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier.testTag("scaffold-root"),
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        containerColor = containerColor,
        contentColor = contentColor,
        content = content,
    )
}

// Previews
@Preview(name = "Empty Light", showBackground = true)
@Composable
private fun PreviewScaffoldEmptyLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsScaffold {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Content") }
        }
    }
}

@Preview(name = "Empty Dark", showBackground = true)
@Composable
private fun PreviewScaffoldEmptyDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsScaffold {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Content") }
        }
    }
}
