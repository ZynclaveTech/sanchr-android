package com.sanchr.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radius tokens from design system: 8, 12, 16, 24, 9999 dp.
 */
object SanchrShapeTokens {
    val CornerSmall = RoundedCornerShape(8.dp)
    val CornerMedium = RoundedCornerShape(12.dp)
    val CornerLarge = RoundedCornerShape(16.dp)
    val CornerExtraLarge = RoundedCornerShape(24.dp)
    val CornerFull = RoundedCornerShape(9999.dp)

    /** Commonly used for message bubbles: rounded on three corners, sharp on one. */
    val BubbleSent = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = 16.dp,
        bottomEnd = 4.dp,
    )

    val BubbleReceived = RoundedCornerShape(
        topStart = 4.dp,
        topEnd = 16.dp,
        bottomStart = 16.dp,
        bottomEnd = 16.dp,
    )
}

/**
 * Material3 Shapes mapped to Sanchr design tokens.
 */
val SanchrShapes = Shapes(
    extraSmall = SanchrShapeTokens.CornerSmall,
    small = SanchrShapeTokens.CornerSmall,
    medium = SanchrShapeTokens.CornerMedium,
    large = SanchrShapeTokens.CornerLarge,
    extraLarge = SanchrShapeTokens.CornerExtraLarge,
)
