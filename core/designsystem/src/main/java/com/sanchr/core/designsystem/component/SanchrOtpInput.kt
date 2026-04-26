package com.sanchr.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.sanchr.core.designsystem.theme.SanchrShapeTokens

/**
 * Filters a raw string to digits only and clamps to [maxLength].
 * Extracted as a top-level helper for unit testing.
 */
internal fun filterOtpInput(
    raw: String,
    maxLength: Int,
): String = raw.filter { it.isDigit() }.take(maxLength)

/**
 * A 6-cell (configurable) OTP input.
 *
 * A hidden [BasicTextField] captures input; a [Row] of [Box]es renders the current
 * character for each index with borders indicating filled/empty/active/error state.
 * Tapping the row requests focus on the hidden text field.
 *
 * The [focusRequester] parameter lets callers programmatically pop the keyboard on
 * navigation entry (iOS-parity with `OTPView`'s `@FocusState` + `.onAppear`). If
 * unset, an internally-remembered requester is used so tap-to-focus still works.
 */
@Composable
fun SanchrOtpInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    isError: Boolean = false,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onComplete: (String) -> Unit = {},
) {
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(value, length) {
        if (value.length == length) {
            onComplete(value)
        }
    }

    /**
     * Safe focus request — `FocusRequester.requestFocus()` throws
     * `IllegalStateException("FocusRequester is not initialized")` if invoked
     * before the receiving node has been attached. The OTP cell click handler
     * is the most likely caller of this race (taps fire before composition
     * settles), and that NPE/ISE is the crash this guards against.
     */
    fun safeRequestFocus() {
        try {
            focusRequester.requestFocus()
            keyboard?.show()
        } catch (_: IllegalStateException) {
            // Focus target not yet attached — fall through; the next click will land.
        }
    }

    Box(modifier = modifier) {
        // Invisible text field that owns focus and keyboard input.
        // We use alpha(0f) + zero-size layout rather than just zero-size, so the
        // focus modifier reliably attaches even when the cell row is the visible
        // hit target.
        BasicTextField(
            value = value,
            onValueChange = { raw ->
                val filtered = filterOtpInput(raw, length)
                if (filtered != value) {
                    onValueChange(filtered)
                }
            },
            modifier =
                Modifier
                    .alpha(0f)
                    .focusRequester(focusRequester)
                    // Keep the field laid out as zero-size so it does not affect layout,
                    // but still receives focus/keyboard.
                    .layout { measurable, _ ->
                        val placeable = measurable.measure(Constraints.fixed(0, 0))
                        layout(0, 0) { placeable.place(0, 0) }
                    },
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                ),
            singleLine = true,
        )

        Row(
            modifier =
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    safeRequestFocus()
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(length) { index ->
                OtpCell(
                    char = value.getOrNull(index),
                    isActive = index == value.length,
                    isError = isError,
                )
            }
        }
    }
}

@Composable
private fun OtpCell(
    char: Char?,
    isActive: Boolean,
    isError: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    val borderColor =
        when {
            isError -> colorScheme.error
            isActive -> colorScheme.primary
            char != null -> colorScheme.outline
            else -> colorScheme.outlineVariant
        }
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .border(
                    width = if (isActive || isError) 2.dp else 1.dp,
                    color = borderColor,
                    shape = SanchrShapeTokens.CornerMedium,
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (char != null) {
            Text(
                text = char.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.onSurface,
            )
        }
    }
}
