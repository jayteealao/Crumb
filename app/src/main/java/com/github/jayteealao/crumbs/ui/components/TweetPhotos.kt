package com.github.jayteealao.crumbs.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout

@Composable
fun TweetPhotos(content: @Composable () -> Unit) {
    Layout(
        content = content
    ) { measurable, constraints ->
        val placeables = measurable.map { it.measure(constraints) }
        val size = placeables.size
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (size == 1) {
                placeables[0].placeRelative(0, 0, 0f)
            } else {
            }
        }
    }
}

fun Modifier.gridLayout(size: Int = 1) = layout { measurable, constraints ->
    val placeables = measurable
    layout(constraints.maxWidth, constraints.maxHeight) {
    }
}
