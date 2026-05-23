package com.github.jayteealao.crumbs.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.github.jayteealao.crumbs.designsystem.R

// Brutalist type pairing: Funnel Display for headlines, IBM Plex Mono for
// everything index/meta/body. Bundled in res/font/ so default Blocking
// loading matches the offline-rendering NFR.
private val FunnelDisplay = FontFamily(
    Font(resId = R.font.funnel_display_regular, weight = FontWeight.Normal),
    Font(resId = R.font.funnel_display_medium, weight = FontWeight.Medium),
    Font(resId = R.font.funnel_display_bold, weight = FontWeight.Bold),
)

private val IBMPlexMono = FontFamily(
    Font(resId = R.font.ibm_plex_mono_regular, weight = FontWeight.Normal),
    Font(resId = R.font.ibm_plex_mono_medium, weight = FontWeight.Medium),
    Font(resId = R.font.ibm_plex_mono_bold, weight = FontWeight.Bold),
)

object CrumbsTypography {
    val displayHeadline = TextStyle(
        fontFamily = FunnelDisplay,
        fontSize = 32.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.6).sp,
    )

    val displaySmall = TextStyle(
        fontFamily = FunnelDisplay,
        fontSize = 22.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
    )

    val titleSection = TextStyle(
        fontFamily = IBMPlexMono,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
    )

    val bodyMono = TextStyle(
        fontFamily = IBMPlexMono,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    )

    val metaMono = TextStyle(
        fontFamily = IBMPlexMono,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.6.sp,
    )

    val captionMono = TextStyle(
        fontFamily = IBMPlexMono,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
    )

    val tagMono = TextStyle(
        fontFamily = IBMPlexMono,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
    )
}
