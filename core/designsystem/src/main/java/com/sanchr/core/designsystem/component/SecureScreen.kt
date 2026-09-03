package com.sanchr.core.designsystem.component

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Marks the current screen as sensitive: applies [WindowManager.LayoutParams.FLAG_SECURE]
 * to the hosting Activity's window for the lifetime of this composable, preventing
 * screenshots, screen recording, and hiding the screen from the app-switcher thumbnail.
 *
 * On leaving composition, the flag is cleared only if it was not already set by someone
 * else (e.g. the global user-toggle wiring in MainActivity), so we don't undo a still-
 * active protection.
 *
 * iOS parity: matches the unconditional screenshot protection applied to OTP and
 * recovery-key screens on iOS (see M6 security checklist).
 */
@Composable
fun SecureScreen() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val prior = (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        onDispose {
            if (!prior) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
