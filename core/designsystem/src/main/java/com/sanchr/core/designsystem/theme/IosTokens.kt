package com.sanchr.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The iOS design tokens, by their iOS names.
 *
 * Android's own [SanchrSpacing] scale is offset from iOS's by one step —
 * Android's `sm` is 8 where iOS's `sm` is 12, Android's `default` is 16 where
 * iOS calls that `md`. A screen ported by matching token *names* therefore
 * came out at the wrong size everywhere, which is most of why the two apps
 * stopped looking alike.
 *
 * These are the semantic values from `SanchrSpacing.swift` and
 * `SanchrRadius.swift`, spelled the same so a port can be read against the
 * Swift side line by line. The base scale is deliberately left alone: dozens
 * of screens use it, and renumbering it would move everything at once.
 */
object IosSpacing {
    // Base scale, as iOS names it.
    val xxxs: Dp = 2.dp
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 20.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 40.dp
    val xxxxl: Dp = 48.dp
    val mega: Dp = 64.dp

    /** The margin a screen's content keeps from the edges. */
    val screenHorizontal: Dp = lg

    /** Inside a card or grouped section, where the screen margin already applies. */
    val sectionHorizontal: Dp = md

    val rootTop: Dp = md
    val sectionGap: Dp = xxl
    val listItemGap: Dp = sm

    // Chats list.
    val chatAvatarSize: Dp = 56.dp
    val statusIndicatorSize: Dp = 16.dp
    val statusIndicatorBorder: Dp = 2.dp
    val searchBarHeight: Dp = 44.dp
    val searchIconSize: Dp = 15.dp
    val filterTabHeight: Dp = 32.dp
    val filterTabHPadding: Dp = md
    val filterTabGap: Dp = xs
    val chatRowHPadding: Dp = lg
    val chatRowVPadding: Dp = sm
    val chatRowHeight: Dp = 80.dp
    val unreadBadgeSize: Dp = lg
    val avatarContentGap: Dp = sm
    val namePreviewGap: Dp = xxs
    val sectionHeaderTop: Dp = xs
    val e2eeIconSize: Dp = sm

    // Chrome.
    val iconButtonSize: Dp = 40.dp
    val fabSize: Dp = 64.dp
    val fabIconSize: Dp = lg
    val tabBarHeight: Dp = 73.dp

    // Chat detail.
    val chatHeaderAvatarSize: Dp = 40.dp
    val chatHeaderStatusDot: Dp = sm
    val chatHeaderActionSize: Dp = 36.dp
    val bubbleHPadding: Dp = md
    val bubbleVPadding: Dp = 10.dp
    val messageGap: Dp = sm
    val composerButtonSize: Dp = 40.dp
    val composerSendSize: Dp = 44.dp

    /** How much of the width one message may take. */
    const val MESSAGE_MAX_WIDTH_FRACTION: Float = 0.75f
}

/** Corner radii, from iOS `SanchrRadius`. */
object IosRadius {
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp

    /** Capsules. iOS uses 9999; anything past half the height is the same shape. */
    val full: Dp = 999.dp

    val card: Dp = md
    val input: Dp = sm
    val button: Dp = md
    val bubble: Dp = lg
    val avatar: Dp = full
    val sheet: Dp = xl

    val bubbleTail: Dp = 4.dp
    val bubbleCompact: Dp = 10.dp
    val bubbleMain: Dp = lg
    val composerInput: Dp = xl
}
