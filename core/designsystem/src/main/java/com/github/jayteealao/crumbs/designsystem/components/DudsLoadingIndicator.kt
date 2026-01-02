package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.DudsColors

/**
 * DudsLoadingIndicator - Circular loading indicator
 *
 * @param modifier Modifier to apply to the indicator
 * @param size Size of the indicator
 */
@Composable
fun DudsLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = DudsColors.secondaryAccent,
        strokeWidth = 3.dp
    )
}

/**
 * DudsLoadingBox - Centered loading indicator in a box
 */
@Composable
fun DudsLoadingBox(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        DudsLoadingIndicator()
    }
}

@Preview(showBackground = true)
@Composable
fun DudsLoadingIndicatorPreview() {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.size(200.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        DudsLoadingIndicator()
    }
}
