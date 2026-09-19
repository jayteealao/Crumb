package com.github.jayteealao.crumbs.designsystem.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// Brutalist direction rejects rounded corners. Cards, chips, search fields,
// dialogs are all RectangleShape. The lone exception is `chip` — a 4dp top-end
// nick that preserves the cut-corner DNA without softening anything.
object CrumbsShapes {
    val card: Shape = RectangleShape
    val cardSmall: Shape = RectangleShape
    val button: Shape = RectangleShape
    val textField: Shape = RectangleShape
    val dialog: Shape = RectangleShape
    val navigationBar: Shape = RectangleShape
    val chip: Shape = CutCornerShape(topEnd = 4.dp)
    val rectangle: Shape = RectangleShape
}

val LocalCrumbsShapes = compositionLocalOf { CrumbsShapes }
