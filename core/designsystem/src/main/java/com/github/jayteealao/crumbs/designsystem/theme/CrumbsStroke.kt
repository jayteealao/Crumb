package com.github.jayteealao.crumbs.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Brutalist stroke scale + offset-shadow specs. Consumed today by the theme
// root provider; OverlayCard wiring (offsetX / offsetY) lands in the
// components slice.
object CrumbsStroke {
    val hairline: Dp = 1.dp
    val regular: Dp = 1.5.dp
    val emphasis: Dp = 2.dp
    val offsetX: Dp = 6.dp
    val offsetY: Dp = 6.dp
}
