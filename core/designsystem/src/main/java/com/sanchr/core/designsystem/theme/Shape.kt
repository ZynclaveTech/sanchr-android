package com.sanchr.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radius tokens from design system: 8, 12, 16, 20, 24, 9999 dp.
 *
 * iOS parity (ios/.../DesignSystem/Radius.swift + ExportComponents.swift):
 *   - SanchrRadius.sm    = 8   -> CornerSmall
 *   - SanchrRadius.md    = 12  -> CornerMedium (base card radius)
 *   - SanchrRadius.lg    = 16  -> CornerLarge
 *   - SanchrExportMetrics.cardRadius   = 20  -> CornerCard (export card radius)
 *   - SanchrExportMetrics.largeRadius  = 24  -> CornerExtraLarge / sheets
 *   - SanchrRadius.full  = 9999 -> CornerFull
 */
object SanchrShapeTokens {
    val CornerSmall = RoundedCornerShape(8.dp)
    val CornerMedium = RoundedCornerShape(12.dp)
    val CornerLarge = RoundedCornerShape(16.dp)

    /**
     * iOS-export card radius. `SanchrExportMetrics.cardRadius = 20` in
     * ios/.../DesignSystem/ExportComponents.swift (line 9). Android previously jumped
     * directly from 16 -> 24 which forced consumer screens to either use the too-tight
     * CornerLarge or the too-round CornerExtraLarge.
     */
    val CornerCard = RoundedCornerShape(20.dp)
    val CornerExtraLarge = RoundedCornerShape(24.dp)
    val CornerFull = RoundedCornerShape(9999.dp)

    /** Commonly used for message bubbles: rounded on three corners, sharp on one. */
    val BubbleSent =
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 16.dp,
            bottomEnd = 4.dp,
        )

    val BubbleReceived =
        RoundedCornerShape(
            topStart = 4.dp,
            topEnd = 16.dp,
            bottomStart = 16.dp,
            bottomEnd = 16.dp,
        )
}

/**
 * Material3 Shapes mapped to Sanchr design tokens.
 *
 * `large` is the slot Material3 `Card` reads by default. iOS export cards render at
 * 20pt (SanchrExportMetrics.cardRadius), so `large` now points at CornerCard instead
 * of CornerLarge (16dp) to match iOS Card visuals without forcing consumer shape overrides.
 */
val SanchrShapes =
    Shapes(
        extraSmall = SanchrShapeTokens.CornerSmall,
        small = SanchrShapeTokens.CornerSmall,
        medium = SanchrShapeTokens.CornerMedium,
        large = SanchrShapeTokens.CornerCard,
        extraLarge = SanchrShapeTokens.CornerExtraLarge,
    )
