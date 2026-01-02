package com.github.jayteealao.crumbs.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.ui.theme.DudsColors
import com.github.jayteealao.crumbs.ui.theme.DudsRadius
import com.github.jayteealao.crumbs.ui.theme.DudsTypography

/**
 * DudsChip - Pill-shaped chip component for tabs and filters
 *
 * @param text The text to display in the chip
 * @param selected Whether the chip is currently selected
 * @param onClick Callback when the chip is clicked
 * @param modifier Modifier to apply to the chip
 */
@Composable
fun DudsChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) DudsColors.secondaryAccent else DudsColors.surface
    val textColor = if (selected) DudsColors.textOnDark else DudsColors.textPrimary
    val border = if (selected) null else BorderStroke(1.dp, DudsColors.border)

    Surface(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        shape = RoundedCornerShape(DudsRadius.full),
        color = backgroundColor,
        border = border,
        elevation = 0.dp
    ) {
        Text(
            text = text,
            style = DudsTypography.chipLabel,
            color = textColor,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DudsChipPreview() {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        DudsChip(text = "for you", selected = true, onClick = {})
        DudsChip(text = "following", selected = false, onClick = {})
        DudsChip(text = "wishlist", selected = false, onClick = {})
    }
}
