package com.sanchr.app.lock

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.sanchr.core.designsystem.component.SanchrButton
import com.sanchr.core.designsystem.theme.SanchrGray400
import com.sanchr.core.designsystem.theme.SanchrTheme

/**
 * Covers the app while App Lock is armed. Nothing behind it is composed, so
 * the transcript is not on screen and does not reach the recents thumbnail.
 *
 * Authentication allows a biometric *or* the device passcode, matching iOS's
 * `deviceOwnerAuthentication`: a biometrics-only prompt tells the user after
 * a lockout to use their passcode while giving them no way to enter it.
 */
@Composable
fun AppLockGate(
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalContext.current.findFragmentActivity()
    var attempted by remember { mutableStateOf(false) }

    fun prompt() {
        if (activity == null) {
            // Nothing can authenticate here; refusing to unlock would strand the user in the app.
            onUnlocked()
            return
        }
        if (!canAuthenticate(activity)) {
            // The lock was set up and the device's own security has since been
            // removed. Keeping the user out of their messages helps no one.
            onUnlocked()
            return
        }
        val callback =
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onUnlocked()
            }
        val info =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle("Unlock Sanchr")
                .setSubtitle("Your chats are locked")
                .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
                .build()
        runCatching { BiometricPrompt(activity, callback).authenticate(info) }
    }

    LaunchedEffect(Unit) {
        if (!attempted) {
            attempted = true
            prompt()
        }
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(SanchrTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))
            Text(text = "Sanchr is locked", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xs))
            Text(
                text = "Unlock with your fingerprint, face or device passcode.",
                style = MaterialTheme.typography.bodySmall,
                color = SanchrGray400,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))
            SanchrButton(text = "Unlock", onClick = { prompt() })
        }
    }
}

/** Whether this device can satisfy a biometric-or-passcode prompt right now. */
internal fun canAuthenticate(activity: FragmentActivity): Boolean =
    BiometricManager.from(activity).canAuthenticate(ALLOWED_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

private const val ALLOWED_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/** The hosting activity, unwrapped through whatever context wrappers Compose is under. */
private tailrec fun Context.findFragmentActivity(): FragmentActivity? =
    when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findFragmentActivity()
        else -> null
    }
