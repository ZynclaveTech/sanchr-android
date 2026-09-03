package com.sanchr.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing tokens from design system: 2, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64 dp.
 * Access via SanchrTheme.spacing (composition local).
 */
data class SanchrSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val default: Dp = 16.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 40.dp,
    val huge: Dp = 48.dp,
    val massive: Dp = 64.dp,
)

val LocalSanchrSpacing = staticCompositionLocalOf { SanchrSpacing() }
