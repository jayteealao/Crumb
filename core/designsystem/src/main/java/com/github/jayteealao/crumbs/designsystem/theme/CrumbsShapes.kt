package com.github.jayteealao.crumbs.designsystem.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Crumbs shape system with cut corners for visual identity
 *
 * Cut corner logic:
 * - Top-end: Actions, forward movement (buttons)
 * - Bottom-end: Content, stability (cards)
 * - Bottom-start: Input, foundation (text fields)
 * - Top-start: Labels, metadata (chips)
 * - Dual cuts: Elevated importance (dialogs)
 */
object CrumbsShapes {
    private val smallCut = 4.dp
    private val mediumCut = 8.dp
    private val largeCut = 12.dp

    // Buttons - top-end (forward action direction)
    val button: Shape = CutCornerShape(topEnd = mediumCut)
    val buttonSmall: Shape = CutCornerShape(topEnd = smallCut)

    // Cards - bottom-end (grounded content)
    val card: Shape = CutCornerShape(bottomEnd = largeCut)
    val cardSmall: Shape = CutCornerShape(bottomEnd = mediumCut)

    // Inputs/Text fields - bottom-start (input area)
    val textField: Shape = CutCornerShape(bottomStart = mediumCut)

    // Chips/Tags - top-start (label/metadata feel)
    val chip: Shape = CutCornerShape(topStart = smallCut)

    // Dialogs/Modals - dual cuts for emphasis
    val dialog: Shape = CutCornerShape(
        topEnd = largeCut,
        bottomStart = largeCut
    )

    // Navigation - bottom-start (anchored)
    val navigationBar: Shape = CutCornerShape(bottomStart = mediumCut)

    // Fully rectangular when needed
    val rectangle: Shape = RectangleShape
}
