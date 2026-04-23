package com.sanchr.core.designsystem.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush

/**
 * Gradient brush definitions used throughout the Sanchr design system.
 */
object SanchrGradients {
    /** Primary gradient: Indigo to Cyan, used for hero elements and CTAs. */
    val Primary =
        Brush.linearGradient(
            colors = listOf(SanchrIndigo500, SanchrCyan500),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )

    /** Dark primary gradient: Deep Indigo to Indigo, used in dark mode headers. */
    val PrimaryDark =
        Brush.linearGradient(
            colors = listOf(SanchrIndigo950, SanchrIndigo700),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )

    /** Accent gradient: Cyan to Indigo, used for secondary actions. */
    val Accent =
        Brush.linearGradient(
            colors = listOf(SanchrCyan400, SanchrIndigo400),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, 0f),
        )

    /** Surface gradient for subtle card backgrounds. */
    val SurfaceLight =
        Brush.verticalGradient(
            colors = listOf(SanchrGray50, SanchrWhite),
        )

    /** Surface gradient for dark mode cards. */
    val SurfaceDark =
        Brush.verticalGradient(
            colors = listOf(SanchrGray900, SanchrGray950),
        )

    /** Overlay gradient for content layered on images. */
    val ScrimBottom =
        Brush.verticalGradient(
            colors =
                listOf(
                    SanchrBlack.copy(alpha = 0f),
                    SanchrBlack.copy(alpha = 0.6f),
                ),
        )

    /** Active call background gradient. */
    val CallActive =
        Brush.radialGradient(
            colors = listOf(SanchrIndigo600, SanchrIndigo950),
        )
}
