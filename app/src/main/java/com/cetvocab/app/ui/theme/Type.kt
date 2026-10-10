package com.cetvocab.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Typography on the Material 3 scale.
 *
 * Two rules matter for this app:
 *  * every size is in `sp`, so the system font scale is respected and text never gets
 *    clipped (no fixed-height containers are used in the screens);
 *  * the word itself uses [wordStyle], a monospace-ish display style with generous line
 *    height so that long words and phonetics wrap instead of truncating.
 */
private val defaultTypography = Typography()

/** Slightly tighter, friendlier line heights for Chinese + English mixed content. */
val AppTypography = Typography(
    displaySmall = defaultTypography.displaySmall.copy(
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineMedium = defaultTypography.headlineMedium.copy(
        fontSize = 26.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineSmall = defaultTypography.headlineSmall.copy(
        fontSize = 22.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = defaultTypography.titleLarge.copy(
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = defaultTypography.titleMedium.copy(
        fontSize = 17.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleSmall = defaultTypography.titleSmall.copy(
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = defaultTypography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = defaultTypography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = defaultTypography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
    labelLarge = defaultTypography.labelLarge.copy(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = defaultTypography.labelMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = defaultTypography.labelSmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
)

/** The headline word on the study/exam card. */
val wordStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 40.sp,
    lineHeight = 52.sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

/** Phonetic transcription style, e.g. /ˈæpl/. */
val phoneticStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 16.sp,
    lineHeight = 24.sp,
)
