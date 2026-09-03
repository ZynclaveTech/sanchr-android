package com.sanchr.core.designsystem.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * iOS-export surface tokens, exposed adaptively via [LocalSanchrSurfaces].
 *
 * Mirrors the four UIKit semantic surfaces from
 * `SanchrShared/DesignSystem/ExportComponents.swift`:
 *  - [surface]      = .secondarySystemBackground
 *  - [surfaceMuted] = .tertiarySystemFill
 *  - [surfaceSoft]  = .systemGroupedBackground
 *  - [line]         = .separator
 *
 * Consumers read via `LocalSanchrSurfaces.current.surface` (or the
 * convenience accessor `SanchrTheme.surfaces.surface`).
 */
@Stable
data class SanchrSurfaces(
    val surface: Color,
    val surfaceMuted: Color,
    val surfaceSoft: Color,
    val line: Color,
)

/** Light-mode surface token bundle, hoisted so it allocates once at class-load. */
private val SanchrSurfacesLight =
    SanchrSurfaces(
        surface = SanchrSurfaceLight,
        surfaceMuted = SanchrSurfaceMutedLight,
        surfaceSoft = SanchrSurfaceSoftLight,
        line = SanchrLineLight,
    )

/** Dark-mode surface token bundle, hoisted so it allocates once at class-load. */
private val SanchrSurfacesDark =
    SanchrSurfaces(
        surface = SanchrSurfaceDark,
        surfaceMuted = SanchrSurfaceMutedDark,
        surfaceSoft = SanchrSurfaceSoftDark,
        line = SanchrLineDark,
    )

/**
 * Default [SanchrSpacing] instance, hoisted to module scope so [SanchrTheme]
 * provides the same singleton on every recomposition rather than allocating a
 * fresh `SanchrSpacing()` each pass.
 */
private val DefaultSanchrSpacing = SanchrSpacing()

/**
 * CompositionLocal for adaptive iOS-parity surface tokens. Default falls back
 * to light values; [SanchrTheme] overrides this with the correct light/dark
 * variant based on `darkTheme`.
 */
val LocalSanchrSurfaces = staticCompositionLocalOf { SanchrSurfacesLight }

/**
 * Sanchr application theme.
 *
 * Wraps Material3 [MaterialTheme] with Sanchr-specific color, typography, shape,
 * and spacing tokens. Also provides [SanchrSpacing] and [SanchrSurfaces] via
 * composition locals.
 *
 * @param darkTheme Whether to use the dark color scheme.
 * @param dynamicColor Whether to use dynamic color (Android 12+). Defaults to false
 *   because Sanchr has a specific brand identity.
 * @param content The composable content.
 */
@Composable
fun SanchrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> SanchrDarkColorScheme
            else -> SanchrLightColorScheme
        }

    val surfaces = remember(darkTheme) { if (darkTheme) SanchrSurfacesDark else SanchrSurfacesLight }

    // Update system bar colors to match the theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalSanchrSpacing provides DefaultSanchrSpacing,
        LocalSanchrSurfaces provides surfaces,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SanchrTypography,
            shapes = SanchrShapes,
            content = content,
        )
    }
}

/**
 * Convenience accessor for Sanchr design tokens within a composable.
 * Usage: `SanchrTheme.spacing.default`, `SanchrTheme.surfaces.line`.
 */
object SanchrTheme {
    val spacing: SanchrSpacing
        @Composable
        get() = LocalSanchrSpacing.current

    val surfaces: SanchrSurfaces
        @Composable
        get() = LocalSanchrSurfaces.current
}
