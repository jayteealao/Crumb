package com.github.jayteealao.crumbs.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Crumbs spacing scale
 */
object CrumbsSpacing {
    /** Smallest layout nudge — chip padding and wordmark badge offsets. */
    val xxs: Dp = 3.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp

    /** Content inset for bookmark cards — keeps body, media, and footer aligned. */
    val cardContentInset: Dp = md + 2.dp

    /** Gap between dashes in dashed borders and dividers. */
    val dashGap: Dp = 3.dp
}
