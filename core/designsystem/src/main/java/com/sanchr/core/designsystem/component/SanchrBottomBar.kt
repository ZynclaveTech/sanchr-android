package com.sanchr.core.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sanchr.core.designsystem.theme.LocalSanchrDarkTheme

/**
 * The app's bottom navigation bar.
 *
 * A thin wrapper on Material 3's [NavigationBar] rather than a bar of our own:
 * the framework component brings the selection indicator, ripple and
 * accessibility behaviour, and none of that is worth rewriting. What it does
 * not bring is a surface that looks right, which is what this fixes.
 *
 * Ported from the Vync app's `MaterialBottomTabs`, whose values were measured
 * against a reference bar rather than guessed. The comments below record what
 * each value is for, because every one of them looks arbitrary until it is
 * changed and the bar goes wrong.
 */
@Composable
fun SanchrBottomBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    // LocalSanchrDarkTheme, not isSystemInDarkTheme: the app resolves its own
    // ThemeMode, which can disagree with the device's.
    val base = if (LocalSanchrDarkTheme.current) DarkBarBlack else MaterialTheme.colorScheme.surface

    // The bar runs BEHIND the system navigation bar rather than stopping above
    // it. Scaffold places the bar above the navigation inset, so on its own it
    // ends short of the screen and the page shows through underneath — a seam
    // where there should be none. Taking the inset as the bar's own padding
    // puts the surface all the way down, with the icons still in the top 56dp.
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    NavigationBar(
        // 56dp. M3's own default is 80dp, which on this layout reads as a
        // shelf rather than a bar.
        modifier =
            modifier
                .height(BarHeight + navBarInset)
                // The hairline is half the effect. Without it the bar has no
                // edge where it meets the content, and a bar with no edge reads
                // as the page simply stopping. Drawn at one physical pixel
                // rather than 1.dp: at 3x density a dp line is three pixels and
                // stops being a hairline. Drawn AFTER the content, because the
                // bar's own surface paints over anything drawn beneath it.
                .drawWithContent {
                    drawContent()
                    // Offset by half the stroke: drawLine centres the stroke on
                    // the line, so drawing at y=0 puts half the pixel above the
                    // bar and half below — two rows at 50% coverage instead of
                    // one at 100%, and neither reaching the outline colour.
                    val strokeWidth = 1f
                    val y = strokeWidth / 2f
                    drawLine(
                        color = outline,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = strokeWidth,
                    )
                },
        // `surface`, NOT `surfaceContainer`. M3 tints its container roles with
        // the primary hue, which is why the default bar reads faintly indigo
        // against a neutral page — the same lightness, a different colour.
        containerColor = base,
        // Zero, or none of the above matters. NavigationBar's default 3dp tonal
        // elevation blends the primary hue into whatever container colour it is
        // given, undoing the line above.
        tonalElevation = 0.dp,
        // Ours to apply, since the height above just made room for it.
        windowInsets = WindowInsets.navigationBars,
        contentColor = MaterialTheme.colorScheme.onSurface,
        content = content,
    )
}

/**
 * The dark bar's colour, taken from Vync's measured value rather than from the
 * theme: our dark `surface` sits noticeably lighter than the page and reads as
 * a grey shelf under it.
 */
private val DarkBarBlack = Color(0xFF040404)

private val BarHeight = 56.dp
