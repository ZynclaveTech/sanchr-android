package com.sanchr.core.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

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
    // The app's own surface in both themes. Vync hardcodes a near-black here,
    // measured against the bar it was matching; Sanchr's dark surface is
    // #1A1A24 (iOS surfaceDark) and the near-black read as a hole punched in
    // the bottom of the page rather than as a bar.
    val base = MaterialTheme.colorScheme.surface

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

private val BarHeight = 56.dp

/** 28dp, not M3's 24dp — measured off the bar this matches. */
private val TabIconSize = 28.dp

/**
 * One tab.
 *
 * Icon only, and no selection pill. M3's indicator is a filled
 * `secondaryContainer` capsule, which on this bar is the only opaque shape in
 * the row and reads as a chip sitting on the surface rather than as a
 * selection; tint alone carries it, which is what the reference bar does.
 *
 * The label is not drawn but is still the content description, so the tab
 * keeps its name for screen readers and for tests.
 */
@Composable
fun RowScope.SanchrBottomBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(TabIconSize),
            )
        },
        colors =
            NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent,
                selectedIconColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        modifier =
            modifier.semantics(mergeDescendants = true) {
                contentDescription = label
            },
    )
}
