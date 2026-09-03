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

// --- Light Color Scheme ---
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
        surface = SanchrWhite,
        onSurface = SanchrGray900,
        surfaceVariant = SanchrGray100,
        onSurfaceVariant = SanchrGray600,
        outline = SanchrGray300,
        outlineVariant = SanchrGray200,
        inverseSurface = SanchrGray900,
        inverseOnSurface = SanchrGray100,
        inversePrimary = SanchrIndigo200,
        surfaceTint = SanchrIndigo500,
    )

// --- Dark Color Scheme ---
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
        background = SanchrGray950,
        onBackground = SanchrGray100,
        surface = SanchrGray900,
        onSurface = SanchrGray100,
        surfaceVariant = SanchrGray800,
        onSurfaceVariant = SanchrGray400,
        outline = SanchrGray600,
        outlineVariant = SanchrGray700,
        inverseSurface = SanchrGray100,
        inverseOnSurface = SanchrGray900,
        inversePrimary = SanchrIndigo600,
        surfaceTint = SanchrIndigo400,
    )
