package com.github.jayteealao.crumbs.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.ui.theme.DudsColors
import com.github.jayteealao.crumbs.ui.theme.DudsRadius
import com.github.jayteealao.crumbs.ui.theme.DudsTypography

/**
 * DudsCard - Base card component with flat design
 *
 * @param modifier Modifier to apply to the card
 * @param onClick Optional click handler (makes card clickable)
 * @param withBorder Whether to show a border
 * @param content The content to display inside the card
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DudsCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    withBorder: Boolean = false,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(DudsRadius.md),
        backgroundColor = DudsColors.surface,
        elevation = 0.dp,
        border = if (withBorder) BorderStroke(1.dp, DudsColors.border) else null
    ) {
        content()
    }
}

/**
 * DudsListCard - Card variant optimized for list items
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DudsListCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    DudsCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        withBorder = false,
        content = content
    )
}

@Preview(showBackground = true)
@Composable
fun DudsCardPreview() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DudsCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Card Title", style = DudsTypography.h2CardTitle)
                Text("Card content goes here", style = DudsTypography.bodyPrimary)
            }
        }

        DudsCard(
            modifier = Modifier.fillMaxWidth(),
            withBorder = true,
            onClick = {}
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Clickable Card", style = DudsTypography.h2CardTitle)
                Text("With border", style = DudsTypography.bodyPrimary)
            }
        }

        DudsListCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("List Card", style = DudsTypography.h2CardTitle)
                Text("Optimized for lists", style = DudsTypography.bodyPrimary)
            }
        }
    }
}
