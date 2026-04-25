package com.sanchr.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing tokens from design system: 2, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64 dp.
 * Access via SanchrTheme.spacing (composition local).
 *
 * The trailing block of properties (`heroTopGap`, `heroBottomGap`, `cardCorner`,
 * `phoneFieldRadius`, `avatarPickerSize`) are iOS-literal layout constants
 * lifted from `SanchrShared/DesignSystem/Spacing.swift` and the LoginView /
 * onboarding screens. Use these only when no semantic token (xs/sm/md/...)
 * fits the iOS source — they exist so screens can match iOS pixel-for-pixel
 * without scattering magic dp values throughout call sites.
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
    /** iOS-literal: LoginView spacer above hero. */
    val heroTopGap: Dp = 54.dp,
    /** LoginView gap between hero and phone field. */
    val heroBottomGap: Dp = 44.dp,
    /** Logo squircle corner + hero card corner. */
    val cardCorner: Dp = 28.dp,
    /** Phone-field rounded-rect radius. */
    val phoneFieldRadius: Dp = 20.dp,
    /** Avatar picker (profile setup) diameter. */
    val avatarPickerSize: Dp = 148.dp,
)

val LocalSanchrSpacing = staticCompositionLocalOf { SanchrSpacing() }
