package com.sanchr.feature.chats

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sanchr.core.designsystem.theme.SanchrGray400
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Drag a message sideways to reply to it, as iOS does.
 *
 * Android could only reply through the long-press menu, so the quickest
 * gesture in every messenger was missing from the busiest screen.
 *
 * The drag follows the finger with resistance past the trigger point, snaps
 * back when released, and fires once — on release past the threshold, with a
 * haptic tick so the gesture is confirmed before the composer moves.
 */
@Composable
fun SwipeToReply(
    onReply: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offsetPx = remember { Animatable(0f) }
    val triggerPx = remember(density) { with(density) { TRIGGER_DISTANCE.toPx() } }
    val maxPx = remember(density) { with(density) { MAX_DISTANCE.toPx() } }
    val current by offsetPx.asState()

    Box(modifier = modifier) {
        // Revealed by the drag itself rather than drawn over the bubble: the
        // icon sits behind the leading edge and the bubble slides off it.
        if (current > 1f) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Reply,
                contentDescription = null,
                tint = SanchrGray400,
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .size(ICON_SIZE),
            )
        }
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(current.roundToInt(), 0) }
                    .pointerInput(onReply) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val reached = offsetPx.value >= triggerPx
                                scope.launch { offsetPx.animateTo(0f) }
                                if (reached) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onReply()
                                }
                            },
                            onDragCancel = { scope.launch { offsetPx.animateTo(0f) } },
                        ) { _, dragAmount ->
                            // Leading-edge only, and resisted past the trigger
                            // so the bubble cannot be dragged across the screen.
                            val next = (offsetPx.value + dragAmount).coerceIn(0f, maxPx)
                            scope.launch { offsetPx.snapTo(next) }
                        }
                    },
        ) {
            content()
        }
    }
}

/** Far enough to be deliberate, short enough to be quick. */
private val TRIGGER_DISTANCE: Dp = 56.dp

/** The bubble stops here however far the finger goes. */
private val MAX_DISTANCE: Dp = 80.dp

private val ICON_SIZE: Dp = 20.dp
