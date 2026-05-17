package com.github.jayteealao.crumbs.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.jayteealao.crumbs.designsystem.components.EmptyState

@Composable
fun MapViewScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        EmptyState(
            title = "Map View Coming Soon",
            message = "Visualize connections between your bookmarks. This feature will show a graph of related content based on shared tags.",
        )
    }
}
