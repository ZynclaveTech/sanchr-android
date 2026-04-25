package com.sanchr.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// --- Primary Indigo Scale ---
val SanchrIndigo50 = Color(0xFFEEF2FF)
val SanchrIndigo100 = Color(0xFFE0E7FF)
val SanchrIndigo200 = Color(0xFFC7D2FE)
val SanchrIndigo300 = Color(0xFFA5B4FC)
val SanchrIndigo400 = Color(0xFF818CF8)
val SanchrIndigo500 = Color(0xFF6366F1) // Primary
val SanchrIndigo600 = Color(0xFF4F46E5)
val SanchrIndigo700 = Color(0xFF4338CA)
val SanchrIndigo800 = Color(0xFF3730A3)
val SanchrIndigo900 = Color(0xFF312E81)
val SanchrIndigo950 = Color(0xFF4C1D95) // Dark primary

// --- Accent Cyan Scale ---
val SanchrCyan50 = Color(0xFFECFEFF)
val SanchrCyan100 = Color(0xFFCFFAFE)
val SanchrCyan200 = Color(0xFFA5F3FC)
val SanchrCyan300 = Color(0xFF67E8F9)
val SanchrCyan400 = Color(0xFF22D3EE)
val SanchrCyan500 = Color(0xFF06B6D4) // Accent
val SanchrCyan600 = Color(0xFF0891B2)
val SanchrCyan700 = Color(0xFF0E7490)

// --- Semantic Colors ---
val SanchrSuccess = Color(0xFF22C55E)
val SanchrSuccessLight = Color(0xFFBBF7D0)
val SanchrSuccessDark = Color(0xFF16A34A)

val SanchrError = Color(0xFFEF4444)
val SanchrErrorLight = Color(0xFFFECACA)
val SanchrErrorDark = Color(0xFFDC2626)

val SanchrWarning = Color(0xFFF59E0B)
val SanchrWarningLight = Color(0xFFFDE68A)

// --- Neutral Grays ---
val SanchrGray50 = Color(0xFFF9FAFB)
val SanchrGray100 = Color(0xFFF3F4F6)
val SanchrGray200 = Color(0xFFE5E7EB)
val SanchrGray300 = Color(0xFFD1D5DB)
val SanchrGray400 = Color(0xFF9CA3AF)
val SanchrGray500 = Color(0xFF6B7280)
val SanchrGray600 = Color(0xFF4B5563)
val SanchrGray700 = Color(0xFF374151)
val SanchrGray800 = Color(0xFF1F2937)
val SanchrGray900 = Color(0xFF111827)
val SanchrGray950 = Color(0xFF030712)

// --- Surface / Background ---
val SanchrWhite = Color(0xFFFFFFFF)
val SanchrBlack = Color(0xFF000000)

// --- Dark-mode tinted neutrals (parity with iOS SanchrColors) ---
// Source: ios/Sanchr-iOS/SanchrShared/DesignSystem/Colors.swift lines 32-39.
// iOS dark mode intentionally uses a slightly blue-violet tinted neutral rather than
// pure gray; matching these values removes the "too black, wrong hue" delta vs iOS.
val SanchrDarkBackground = Color(0xFF0F0F14) // iOS backgroundDark
val SanchrDarkSurface = Color(0xFF1A1A24) // iOS surfaceDark
val SanchrDarkSurfaceElevated = Color(0xFF24243A) // iOS surfaceElevatedDark
val SanchrDarkBorder = Color(0xFF2D2D3F) // iOS borderDark
val SanchrDarkDivider = Color(0xFF1F1F2E) // iOS dividerDark

// ---- iOS export tokens ----
// Parity with SanchrShared/DesignSystem/ExportComponents.swift:29-39 — these mirror
// the iOS UIKit semantic color exports used by onboarding / login surfaces:
//   .secondarySystemBackground   -> SanchrSurface{Light,Dark}
//   .tertiarySystemFill          -> SanchrSurfaceMuted{Light,Dark}
//   .systemGroupedBackground     -> SanchrSurfaceSoft{Light,Dark}
//   .separator                   -> SanchrLine{Light,Dark}
val SanchrSurfaceLight = Color(0xFFF2F2F7)
val SanchrSurfaceDark = Color(0xFF1C1C1E)
val SanchrSurfaceMutedLight = Color(0x33767680)
val SanchrSurfaceMutedDark = Color(0x5C767680)
val SanchrSurfaceSoftLight = Color(0xFFF2F2F7)
val SanchrSurfaceSoftDark = Color(0xFF000000)
val SanchrLineLight = Color(0x4A3C3C43)

// Note: plan H1 listed `0x995454548` (9 hex digits, malformed). Corrected to the
// valid 8-digit ARGB literal `0x99545458` matching iOS .separator dark variant.
val SanchrLineDark = Color(0x99545458)

// --- Light Color Scheme ---
// Parity with iOS SanchrColors light-mode semantics (Colors.swift lines 21-28):
//   background = #FFFFFF, surface = #F9FAFB (subtle gray, NOT pure white),
//   textPrimary = #111827 (gray900), textSecondary = #6B7280 (gray500),
//   border = #E5E7EB (gray200), divider = #F3F4F6 (gray100).
val SanchrLightColorScheme =
    lightColorScheme(
        primary = SanchrIndigo500,
        onPrimary = SanchrWhite,
        primaryContainer = SanchrIndigo100,
        onPrimaryContainer = SanchrIndigo900,
        secondary = SanchrCyan500,
        onSecondary = SanchrWhite,
        secondaryContainer = SanchrCyan100,
        onSecondaryContainer = SanchrCyan700,
        tertiary = SanchrIndigo400,
        onTertiary = SanchrWhite,
        error = SanchrError,
        onError = SanchrWhite,
        errorContainer = SanchrErrorLight,
        onErrorContainer = SanchrErrorDark,
        background = SanchrWhite,
        onBackground = SanchrGray900,
        // iOS surfaceLight = #F9FAFB — subtle tint vs background. Using SanchrGray50
        // (same hex) ensures Card/surface elements visually separate from the background
        // matching iOS instead of vanishing into a flat white plane.
        surface = SanchrGray50,
        onSurface = SanchrGray900,
        surfaceVariant = SanchrGray100,
        // iOS textSecondaryLight = #6B7280 (gray500). Android was using gray600 (#4B5563)
        // which is noticeably darker and made secondary text look almost as strong as
        // primary text. Parity: use SanchrGray500.
        onSurfaceVariant = SanchrGray500,
        // iOS borderLight = #E5E7EB (gray200). Android previously used gray300 which is
        // too heavy and produced visible borders where iOS shows a soft hairline.
        outline = SanchrGray200,
        outlineVariant = SanchrGray100,
        inverseSurface = SanchrGray900,
        inverseOnSurface = SanchrGray100,
        inversePrimary = SanchrIndigo200,
        surfaceTint = SanchrIndigo500,
    )

// --- Dark Color Scheme ---
// Parity with iOS SanchrColors dark-mode semantics (Colors.swift lines 32-39):
//   background = #0F0F14, surface = #1A1A24, surfaceElevated = #24243A,
//   textPrimary = #F9FAFB (gray50), textSecondary = #9CA3AF (gray400),
//   border = #2D2D3F, divider = #1F1F2E.
val SanchrDarkColorScheme =
    darkColorScheme(
        primary = SanchrIndigo400,
        onPrimary = SanchrIndigo950,
        primaryContainer = SanchrIndigo800,
        onPrimaryContainer = SanchrIndigo100,
        secondary = SanchrCyan400,
        onSecondary = SanchrCyan700,
        secondaryContainer = SanchrCyan700,
        onSecondaryContainer = SanchrCyan100,
        tertiary = SanchrIndigo300,
        onTertiary = SanchrIndigo900,
        error = SanchrErrorLight,
        onError = SanchrErrorDark,
        errorContainer = SanchrErrorDark,
        onErrorContainer = SanchrErrorLight,
        background = SanchrDarkBackground,
        // iOS textPrimaryDark = #F9FAFB (gray50). Previously Android used gray100 (#F3F4F6)
        // which is subtly dimmer. Align to gray50 for parity.
        onBackground = SanchrGray50,
        surface = SanchrDarkSurface,
        onSurface = SanchrGray50,
        surfaceVariant = SanchrDarkSurfaceElevated,
        onSurfaceVariant = SanchrGray400,
        outline = SanchrDarkBorder,
        outlineVariant = SanchrDarkDivider,
        inverseSurface = SanchrGray100,
        inverseOnSurface = SanchrGray900,
        inversePrimary = SanchrIndigo600,
        surfaceTint = SanchrIndigo400,
    )
