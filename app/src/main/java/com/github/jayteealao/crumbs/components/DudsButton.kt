package com.github.jayteealao.crumbs.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.ui.theme.DudsColors
import com.github.jayteealao.crumbs.ui.theme.DudsRadius
import com.github.jayteealao.crumbs.ui.theme.DudsTypography

/**
 * DudsPrimaryButton - Black background with white text
 */
@Composable
fun DudsPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(DudsRadius.full),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = DudsColors.secondaryAccent,
            contentColor = DudsColors.textOnDark,
            disabledBackgroundColor = DudsColors.disabledText,
            disabledContentColor = DudsColors.textOnDark
        ),
        elevation = ButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
        Text(
            text = text,
            style = DudsTypography.buttonLarge
        )
    }
}

/**
 * DudsAccentButton - Yellow background with black text
 */
@Composable
fun DudsAccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(DudsRadius.full),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = DudsColors.primaryAccent,
            contentColor = DudsColors.textOnAccent,
            disabledBackgroundColor = DudsColors.mutedFill,
            disabledContentColor = DudsColors.disabledText
        ),
        elevation = ButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
        Text(
            text = text,
            style = DudsTypography.buttonLarge
        )
    }
}

/**
 * DudsGhostButton - Transparent with border
 */
@Composable
fun DudsGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        enabled = enabled,
        shape = RoundedCornerShape(DudsRadius.full),
        colors = ButtonDefaults.outlinedButtonColors(
            backgroundColor = Color.Transparent,
            contentColor = DudsColors.textPrimary,
            disabledContentColor = DudsColors.disabledText
        ),
        border = BorderStroke(1.dp, if (enabled) DudsColors.border else DudsColors.disabledText),
        elevation = ButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
        Text(
            text = text,
            style = DudsTypography.buttonSmall
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DudsButtonsPreview() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DudsPrimaryButton(text = "Connect with X", onClick = {})
        DudsAccentButton(text = "Save to Board", onClick = {})
        DudsGhostButton(text = "Cancel", onClick = {})
        DudsPrimaryButton(text = "Disabled", onClick = {}, enabled = false)
    }
}
