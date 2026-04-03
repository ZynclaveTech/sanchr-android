package com.sanchr.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Sanchr typography scale.
 *
 * Design tokens call for two typefaces:
 *   Afacad -- display / heading font
 *   Inter  -- body / utility font
 *
 * TODO: Once the actual .ttf files are added to res/font/, replace the
 *       default font families below with proper FontFamily declarations:
 *
 *   val AfacadFontFamily = FontFamily(
 *       Font(R.font.afacad_regular, FontWeight.Normal),
 *       Font(R.font.afacad_medium, FontWeight.Medium),
 *       Font(R.font.afacad_semibold, FontWeight.SemiBold),
 *       Font(R.font.afacad_bold, FontWeight.Bold),
 *   )
 *
 *   val InterFontFamily = FontFamily(
 *       Font(R.font.inter_regular, FontWeight.Normal),
 *       Font(R.font.inter_medium, FontWeight.Medium),
 *       Font(R.font.inter_semibold, FontWeight.SemiBold),
 *       Font(R.font.inter_bold, FontWeight.Bold),
 *   )
 *
 * Font files should be placed in res/font/ as:
 *   afacad_regular.ttf, afacad_medium.ttf, afacad_semibold.ttf, afacad_bold.ttf
 *   inter_regular.ttf, inter_medium.ttf, inter_semibold.ttf, inter_bold.ttf
 */

val AfacadFontFamily = FontFamily.SansSerif
val InterFontFamily = FontFamily.SansSerif

/**
 * Design token sizes: 10, 12, 14, 16, 18, 20, 24, 30, 48 sp
 *
 * Mapping to Material3 typography roles:
 *   displayLarge  = 48sp Afacad Bold
 *   displayMedium = 30sp Afacad Bold
 *   displaySmall  = 24sp Afacad SemiBold
 *   headlineLarge = 24sp Afacad SemiBold
 *   headlineMedium= 20sp Afacad SemiBold
 *   headlineSmall = 18sp Afacad Medium
 *   titleLarge    = 20sp Afacad SemiBold
 *   titleMedium   = 16sp Afacad Medium
 *   titleSmall    = 14sp Afacad Medium
 *   bodyLarge     = 16sp Inter Normal
 *   bodyMedium    = 14sp Inter Normal
 *   bodySmall     = 12sp Inter Normal
 *   labelLarge    = 14sp Inter Medium
 *   labelMedium   = 12sp Inter Medium
 *   labelSmall    = 10sp Inter Medium
 */
val SanchrTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = AfacadFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = AfacadFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.25).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = AfacadFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = AfacadFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = AfacadFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = AfacadFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = AfacadFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = AfacadFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = AfacadFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    ),
)
