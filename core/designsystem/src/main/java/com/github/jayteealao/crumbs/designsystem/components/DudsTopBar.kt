package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.DudsColors
import com.github.jayteealao.crumbs.designsystem.theme.DudsTypography

/**
 * DudsTopBar - Transparent top bar with left/center/right slots
 *
 * @param leftText Text to display on the left (e.g., "duds", "back")
 * @param centerIcon Optional icon to display in the center
 * @param rightText Text to display on the right (e.g., "search")
 * @param onLeftClick Callback for left text click
 * @param onCenterClick Callback for center icon click
 * @param onRightClick Callback for right text click
 */
@Composable
fun DudsTopBar(
    leftText: String = "",
    centerIcon: ImageVector? = null,
    rightText: String = "",
    onLeftClick: (() -> Unit)? = null,
    onCenterClick: (() -> Unit)? = null,
    onRightClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left slot
            if (leftText.isNotEmpty()) {
                TextButton(onClick = onLeftClick ?: {}) {
                    Text(
                        text = leftText,
                        style = DudsTypography.buttonSmall,
                        color = DudsColors.textPrimary
                    )
                }
            } else {
                Box(modifier = Modifier.size(48.dp)) // Spacer
            }

            // Right slot
            if (rightText.isNotEmpty()) {
                TextButton(onClick = onRightClick ?: {}) {
                    Text(
                        text = rightText,
                        style = DudsTypography.buttonSmall,
                        color = DudsColors.textPrimary
                    )
                }
            } else {
                Box(modifier = Modifier.size(48.dp)) // Spacer
            }
        }

        // Center slot (absolutely positioned)
        if (centerIcon != null) {
            IconButton(
                onClick = onCenterClick ?: {},
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = centerIcon,
                    contentDescription = null,
                    tint = DudsColors.textPrimary
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DudsTopBarPreview() {
    androidx.compose.foundation.layout.Column {
        DudsTopBar(
            leftText = "crumbs",
            centerIcon = Icons.Default.Star,
            rightText = "search"
        )

        DudsTopBar(
            leftText = "back",
            rightText = "search"
        )
    }
}
