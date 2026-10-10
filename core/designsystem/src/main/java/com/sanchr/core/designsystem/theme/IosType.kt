package com.sanchr.core.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The iOS type scale, by its iOS names.
 *
 * Material's names do not line up with the design's: `bodyMedium` is a role,
 * `conversationPreview` is a decision. Porting a screen meant guessing which
 * Material style was nearest, and the guesses drifted — a preview line that
 * should be 14 medium came out 16 regular, and the row grew.
 *
 * These are `SanchrTypography.swift`'s semantic styles with the same names,
 * sizes and weights, so a ported screen can be checked against the Swift one
 * without a lookup table. Afacad on both sides.
 *
 * Line heights are left unset: Compose then uses the font's own metrics,
 * which is what SwiftUI does with these faces. Pinning a line height here
 * made the two platforms disagree about row heights more, not less.
 */
object IosType {
    // Sizes, as iOS enumerates them.
    private val xxxs: TextUnit = 10.sp
    private val xxs: TextUnit = 12.sp
    private val xs: TextUnit = 14.sp
    private val sm: TextUnit = 16.sp
    private val md: TextUnit = 18.sp
    private val lg: TextUnit = 20.sp
    private val xl: TextUnit = 24.sp
    private val xxl: TextUnit = 30.sp
    private val display: TextUnit = 36.sp
    private val hero: TextUnit = 48.sp

    private fun style(
        size: TextUnit,
        weight: FontWeight,
        letterSpacing: TextUnit = 0.sp,
    ) = TextStyle(
        fontFamily = Afacad,
        fontSize = size,
        fontWeight = weight,
        letterSpacing = letterSpacing,
    )

    val heroTitle = style(hero, FontWeight.Bold)
    val screenTitle = style(xxl, FontWeight.SemiBold)
    val displayTitle = style(display, FontWeight.SemiBold)
    val sectionHeader = style(xl, FontWeight.SemiBold)
    val cardTitle = style(lg, FontWeight.SemiBold)
    val bodyLarge = style(md, FontWeight.Medium)
    val body = style(sm, FontWeight.Medium)
    val bodyBold = style(sm, FontWeight.SemiBold)
    val caption = style(xs, FontWeight.Normal)
    val captionSmall = style(xxs, FontWeight.Normal)
    val micro = style(xxxs, FontWeight.Normal)
    val button = style(sm, FontWeight.SemiBold)
    val tabLabel = style(xxs, FontWeight.SemiBold)
    val chatMessage = style(sm, FontWeight.Medium)
    val messageBubbleText = style(xs, FontWeight.Medium)
    val messageTimestamp = style(xxs, FontWeight.Normal)
    val chatTimestamp = style(xxs, FontWeight.Medium)
    val searchPlaceholder = style(xs, FontWeight.Medium)

    /** The chats list's own title, which is heavier than a screen title. */
    val chatListTitle = style(xl, FontWeight.Bold)

    /** Negative tracking, as iOS: at 16 semibold the default spacing reads loose. */
    val conversationName = style(sm, FontWeight.SemiBold, letterSpacing = (-0.5).sp)
    val conversationPreview = style(xs, FontWeight.Medium)
    val conversationPreviewBold = style(xs, FontWeight.SemiBold)
    val filterTab = style(xs, FontWeight.SemiBold)
    val filterTabActive = style(xs, FontWeight.Bold)

    /** Letterspaced, as iOS: small caps need the air to stay readable. */
    val sectionLabel = style(xxs, FontWeight.SemiBold, letterSpacing = 0.5.sp)
    val unreadBadge = style(xxs, FontWeight.Bold)
    val e2eeBadge = style(xxxs, FontWeight.Medium)
}
