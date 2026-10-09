package com.sanchr.core.designsystem.theme

import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

/**
 * The chat bubble shape the user chose.
 *
 * A composition local rather than a parameter threaded through the transcript:
 * the shape is read deep inside the message bubble, and every composable
 * between it and the screen would otherwise carry a value none of them use.
 *
 * "modern" is the default on both platforms. iOS reads the same three values
 * from `sanchr.chatBubbleStyle`.
 */
val LocalBubbleStyle = compositionLocalOf<State<String>> { mutableStateOf("modern") }
