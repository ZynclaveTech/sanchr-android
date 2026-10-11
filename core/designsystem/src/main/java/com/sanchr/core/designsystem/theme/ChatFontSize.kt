package com.sanchr.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextStyle

/**
 * The size the user chose for the text of messages.
 *
 * Settings → Appearance has a Font Size slider. It moved, its value went to
 * the server, and no text anywhere changed size: nothing on the phone kept
 * the value and nothing that draws text read it. iOS applies its equivalent
 * to the message bubble, which is where this is applied too.
 *
 * A composition local for the same reason as [LocalBubbleStyle]: it is read
 * deep inside the bubble.
 */
val LocalChatFontSize = compositionLocalOf<State<String>> { mutableStateOf(ChatFontSize.DEFAULT) }

object ChatFontSize {
    const val SMALL = "small"
    const val MEDIUM = "medium"
    const val LARGE = "large"
    const val EXTRA_LARGE = "extra_large"
    const val DEFAULT = MEDIUM

    /** In the order the slider shows them. */
    val options = listOf(SMALL, MEDIUM, LARGE, EXTRA_LARGE)

    private const val SMALL_SCALE = 0.87f
    private const val LARGE_SCALE = 1.15f
    private const val EXTRA_LARGE_SCALE = 1.3f

    /**
     * How much bigger or smaller than usual. On top of the system's own font
     * scale, not instead of it. A value this build does not know — one stored
     * by a newer one — is the usual size, not a guess.
     */
    fun scaleOf(size: String): Float =
        when (size) {
            SMALL -> SMALL_SCALE
            LARGE -> LARGE_SCALE
            EXTRA_LARGE -> EXTRA_LARGE_SCALE
            else -> 1f
        }

    /** [style] at the chosen size, line height included so larger text is not cramped. */
    fun apply(
        style: TextStyle,
        size: String,
    ): TextStyle {
        val scale = scaleOf(size)
        if (scale == 1f) return style
        return style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale)
    }
}

/** The text style for the body of a message, at the size the user chose. */
val messageBodyTextStyle: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = ChatFontSize.apply(IosType.messageBubbleText, LocalChatFontSize.current.value)
