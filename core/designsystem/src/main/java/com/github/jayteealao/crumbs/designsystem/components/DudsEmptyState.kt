package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.jayteealao.crumbs.designsystem.theme.DudsColors
import com.github.jayteealao.crumbs.designsystem.theme.DudsTypography

/**
 * DudsEmptyState - Empty state component with icon, title, subtitle, and optional CTA
 *
 * @param icon Icon to display
 * @param title Main title text
 * @param subtitle Optional subtitle text
 * @param buttonText Optional button text
 * @param onButtonClick Optional button click callback
 */
@Composable
fun DudsEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    buttonText: String? = null,
    onButtonClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = DudsColors.textSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = DudsTypography.h2CardTitle.copy(fontSize = 18.sp),
            color = DudsColors.textPrimary,
            textAlign = TextAlign.Center
        )

        if (subtitle != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = DudsTypography.bodyPrimary,
                color = DudsColors.textSecondary,
                textAlign = TextAlign.Center
            )
        }

        if (buttonText != null && onButtonClick != null) {
            Spacer(modifier = Modifier.height(24.dp))
            DudsPrimaryButton(
                text = buttonText,
                onClick = onButtonClick
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DudsEmptyStatePreview() {
    DudsEmptyState(
        icon = Icons.Default.Info,
        title = "No bookmarks yet",
        subtitle = "Save your first tweet to get started",
        buttonText = "Browse Twitter",
        onButtonClick = {}
    )
}
