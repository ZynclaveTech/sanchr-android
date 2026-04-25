package com.sanchr.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sanchr.core.designsystem.R

/**
 * Sanchr typography scale.
 *
 * Afacad variable font, copied from iOS at
 * `ios/Sanchr-iOS/Resources/Fonts/Afacad-Variable.ttf`. The Compose runtime
 * picks the closest weight axis on first composition. Available weights
 * Regular(400) / Medium(500) / SemiBold(600) / Bold(700).
 *
 * iOS parity citations preserved from prior phase (Phase 6f-1) — only the
 * `fontFamily` slot is swapped from the previous SansSerif fallback to the
 * bundled Afacad variable font. Sizes / weights / line-heights / letter-spacings
 * are intentionally untouched so Phase 6f-1 parity holds.
 */
@OptIn(ExperimentalTextApi::class)
private val Afacad =
    FontFamily(
        Font(
            R.font.afacad_variable,
            FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400)),
        ),
        Font(
            R.font.afacad_variable,
            FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(500)),
        ),
        Font(
            R.font.afacad_variable,
            FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(600)),
        ),
        Font(
            R.font.afacad_variable,
            FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700)),
        ),
    )

/**
 * Design token sizes: 10, 12, 14, 16, 18, 20, 24, 30, 48 sp
 *
 * iOS parity notes (ios/.../DesignSystem/Typography.swift):
 *   - iOS `body`        = 16pt **Medium**   (Typography.swift line 91)
 *   - iOS `bodyBold`    = 16pt **SemiBold** (line 94)
 *   - iOS `bodyLarge`   = 18pt **Medium**   (line 88)
 *   - iOS `button`      = 16pt **SemiBold** (line 106)
 *   - iOS `displayTitle`= 36pt **SemiBold** (line 79)
 *
 * Material3 defaults to Normal body / Medium labelLarge. To match the heavier
 * iOS look used on chat, cards, and buttons we shift body weights up one step
 * and align labelLarge (button) to 16sp SemiBold.
 */
val SanchrTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = Afacad,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp,
                lineHeight = 56.sp,
                letterSpacing = (-0.5).sp,
            ),
        // iOS parity: `displayTitle` = 36pt SemiBold (Typography.swift line 79).
        // Previously this slot was 30sp Bold which skipped the 36 stop and over-weighted
        // the hero headings that iOS renders at SemiBold.
        displayMedium =
            TextStyle(
                fontFamily = Afacad,
                fontWeight = FontWeight.SemiBold,
                fontSize = 36.sp,
                lineHeight = 44.sp,
                letterSpacing = (-0.25).sp,
            ),
        // iOS `screenTitle` = 30pt SemiBold (Typography.swift line 76).
        displaySmall =
            TextStyle(
                fontFamily = Afacad,
                fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp,
                lineHeight = 38.sp,
                letterSpacing = 0.sp,
            ),
        // iOS `sectionHeader` = 24pt SemiBold (Typography.swift line 82).
        headlineLarge =
            TextStyle(
                fontFamily = Afacad,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                letterSpacing = 0.sp,
            ),
        // iOS `cardTitle` = 20pt SemiBold (Typography.swift line 85).
        headlineMedium =
            TextStyle(
                fontFamily = Afacad,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        // iOS `bodyLarge` = 18pt Medium (Typography.swift line 88).
        headlineSmall =
            TextStyle(
                fontFamily = Afacad,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                lineHeight = 26.sp,
                letterSpacing = 0.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = Afacad,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        // iOS `conversationName` = 16pt SemiBold with -0.5 tracking (Typography.swift line 132).
        titleMedium =
            TextStyle(
                fontFamily = Afacad,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = (-0.5).sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = Afacad,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        // iOS `body` = 16pt **Medium** (Typography.swift line 91). Material3 default is
        // Normal; bumping to Medium matches iOS body weight across cards and content text.
        bodyLarge =
            TextStyle(
                fontFamily = Afacad,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.15.sp,
            ),
        // iOS `caption` = 14pt Regular (Typography.swift line 97). Keep Normal to match.
        bodyMedium =
            TextStyle(
                fontFamily = Afacad,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.25.sp,
            ),
        // iOS `captionSmall` = 12pt Regular (Typography.swift line 100).
        bodySmall =
            TextStyle(
                fontFamily = Afacad,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.4.sp,
            ),
        // iOS `button` = 16pt SemiBold (Typography.swift line 106). Material3's default
        // labelLarge is 14sp Medium which is both smaller and lighter than iOS. Align.
        labelLarge =
            TextStyle(
                fontFamily = Afacad,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.1.sp,
            ),
        // iOS `sectionLabel` = 12pt SemiBold with +0.5 tracking (Typography.swift line 147/159).
        labelMedium =
            TextStyle(
                fontFamily = Afacad,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
        // iOS `micro`/`e2eeBadge` = 10pt Regular/Medium (Typography.swift lines 103/153).
        labelSmall =
            TextStyle(
                fontFamily = Afacad,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                letterSpacing = 0.5.sp,
            ),
    )

/**
 * "STEP X OF 3" eyebrow — 10sp, 2.5sp letterSpacing.
 *
 * iOS parity: onboarding eyebrow tracking from
 * `SanchrShared/DesignSystem/Typography.swift`. Color is applied at the use site
 * (typically `MaterialTheme.colorScheme.primary`).
 */
val SanchrMicroEyebrowStyle: TextStyle =
    TextStyle(
        fontFamily = Afacad,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        letterSpacing = 2.5.sp,
    )
