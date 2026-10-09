package com.sanchr.core.designsystem.theme

import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

/**
 * Whether the keyboard's return key sends the message instead of inserting a
 * newline.
 *
 * Threaded as a composition local for the same reason as [LocalBubbleStyle]:
 * only the composer's text field cares, and every composable between it and the
 * screen would otherwise carry a value none of them use.
 *
 * iOS defaults this to `true` and reads the same stored value from
 * `sanchr.enterSendsMessage` in `ChatInputBarView`; the Android default below
 * matches so a fresh install behaves identically on both platforms.
 */
val LocalEnterSendsMessage = compositionLocalOf<State<Boolean>> { mutableStateOf(true) }
