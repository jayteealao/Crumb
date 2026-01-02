package com.github.jayteealao.crumbs.designsystem.theme

import androidx.compose.material.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Duds Design System Typography
val Typography = Typography(
    // Map Duds typography to Material typography slots
    h4 = DudsTypography.h1Section.copy(
        fontFamily = FontFamily.SansSerif
    ),
    h6 = DudsTypography.h2CardTitle.copy(
        fontFamily = FontFamily.SansSerif
    ),
    body1 = DudsTypography.bodyPrimary.copy(
        fontFamily = FontFamily.SansSerif
    ),
    body2 = DudsTypography.bodySecondary.copy(
        fontFamily = FontFamily.SansSerif
    ),
    button = DudsTypography.buttonLarge.copy(
        fontFamily = FontFamily.SansSerif
    ),
    caption = DudsTypography.caption.copy(
        fontFamily = FontFamily.SansSerif
    ),
    subtitle1 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    subtitle2 = DudsTypography.chipLabel.copy(
        fontFamily = FontFamily.SansSerif
    )
)
