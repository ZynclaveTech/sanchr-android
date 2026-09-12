package com.sanchr.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrButton
import com.sanchr.core.designsystem.component.SanchrCard
import com.sanchr.core.designsystem.component.SanchrTextButton
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrGray400
import com.sanchr.core.designsystem.theme.SanchrTheme
import kotlinx.coroutines.launch

/** The steps of iOS `RegistrationLockView`. */
private enum class LockPhase { IDLE, VERIFY_CURRENT_FOR_CHANGE, ENTER_NEW, CONFIRM_NEW, ENTER_CURRENT_TO_DISABLE }

internal const val REGISTRATION_PIN_LENGTH = 6

/**
 * Enable, change or disable the Registration Lock PIN (iOS
 * `RegistrationLockView`). Someone who gets hold of the phone number cannot
 * re-register it on a new device without the PIN.
 */
@Composable
fun RegistrationLockScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(LockPhase.IDLE) }
    var pendingPin by remember { mutableStateOf("") }
    var currentPinForChange by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun submit(
        enabled: Boolean,
        pin: String,
        currentPin: String,
        success: String,
    ) {
        busy = true
        scope.launch {
            val failure = viewModel.setRegistrationLock(enabled, pin, currentPin)
            busy = false
            if (failure == null) {
                notice = success
                phase = LockPhase.IDLE
            } else {
                error = failure
            }
            pendingPin = ""
            currentPinForChange = ""
        }
    }

    Scaffold(
        // The NavHost's Scaffold has already inset this for the system
        // bars; applying them again counts the status bar twice.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SanchrTopBar(title = "Registration Lock", onNavigateBack = {
                if (phase ==
                    LockPhase.IDLE
                ) {
                    onNavigateBack()
                } else {
                    phase = LockPhase.IDLE
                }
            })
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        innerPadding,
                    ).verticalScroll(rememberScrollState())
                    .padding(SanchrTheme.spacing.default),
        ) {
            when (phase) {
                LockPhase.IDLE ->
                    IdleContent(
                        enabled = uiState.registrationLockEnabled,
                        busy = busy,
                        notice = notice,
                        error = error,
                        onEnable = { phase = LockPhase.ENTER_NEW },
                        onChange = { phase = LockPhase.VERIFY_CURRENT_FOR_CHANGE },
                        onDisable = { phase = LockPhase.ENTER_CURRENT_TO_DISABLE },
                    )
                LockPhase.VERIFY_CURRENT_FOR_CHANGE ->
                    PinEntry(
                        title = "Enter current PIN",
                        subtitle = "Confirm the PIN you use today before choosing a new one.",
                        error = error,
                        onComplete = { pin ->
                            error = null
                            currentPinForChange = pin
                            phase = LockPhase.ENTER_NEW
                        },
                        onCancel = { phase = LockPhase.IDLE },
                    )
                LockPhase.ENTER_NEW ->
                    PinEntry(
                        title = "Create PIN",
                        subtitle = "Choose a 6-digit PIN you'll remember. You'll need it to re-register.",
                        error = error,
                        onComplete = { pin ->
                            error = null
                            pendingPin = pin
                            phase = LockPhase.CONFIRM_NEW
                        },
                        onCancel = { phase = LockPhase.IDLE },
                    )
                LockPhase.CONFIRM_NEW ->
                    PinEntry(
                        title = "Confirm PIN",
                        subtitle = "Enter the same PIN again.",
                        error = error,
                        onComplete = { confirmed ->
                            if (confirmed == pendingPin) {
                                submit(
                                    enabled = true,
                                    pin = confirmed,
                                    currentPin = currentPinForChange,
                                    success = "Registration Lock is on",
                                )
                            } else {
                                error = "PINs didn't match. Try again."
                                pendingPin = ""
                                phase = LockPhase.ENTER_NEW
                            }
                        },
                        onCancel = { phase = LockPhase.ENTER_NEW },
                    )
                LockPhase.ENTER_CURRENT_TO_DISABLE ->
                    PinEntry(
                        title = "Enter PIN",
                        subtitle = "Enter your current Registration Lock PIN to turn it off.",
                        error = error,
                        onComplete = { pin -> submit(enabled = false, pin = "", currentPin = pin, success = "Registration Lock is off") },
                        onCancel = { phase = LockPhase.IDLE },
                    )
            }
        }
    }
}

@Composable
private fun IdleContent(
    enabled: Boolean,
    busy: Boolean,
    notice: String?,
    error: String?,
    onEnable: () -> Unit,
    onChange: () -> Unit,
    onDisable: () -> Unit,
) {
    SanchrCard {
        Row(modifier = Modifier.fillMaxWidth().padding(SanchrTheme.spacing.default), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (enabled) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else SanchrGray400,
                modifier = Modifier.size(32.dp),
            )
            Spacer(modifier = Modifier.width(SanchrTheme.spacing.md))
            Column {
                Text(
                    text = if (enabled) "Enabled" else "Disabled",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (enabled) "Your account is protected with a PIN" else "Anyone with your number could re-register",
                    style = MaterialTheme.typography.bodySmall,
                    color = SanchrGray400,
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(SanchrTheme.spacing.default))
    SanchrCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(SanchrTheme.spacing.default),
            verticalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.sm),
        ) {
            Text(text = "How it works", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                text = "• If someone tries to register with your number on a new device, they'll need your PIN.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "• Choose a PIN you'll remember; losing it means a 7-day waiting period to regain access.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(text = "• You can change or disable the lock at any time.", style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))
    notice?.let { Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
    error?.let { Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))
    if (enabled) {
        SanchrButton(text = "Change PIN", onClick = onChange, enabled = !busy, isLoading = busy, modifier = Modifier.fillMaxWidth())
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SanchrTextButton(onClick = onDisable) {
                Text(text = "Turn off Registration Lock", color = MaterialTheme.colorScheme.error)
            }
        }
    } else {
        SanchrButton(
            text = "Turn on Registration Lock",
            onClick = onEnable,
            enabled = !busy,
            isLoading = busy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A 6-digit PIN field (iOS `PINEntryView`); completes once all digits are in. */
@Composable
internal fun PinEntry(
    title: String,
    subtitle: String,
    error: String?,
    onComplete: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
    Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = SanchrGray400)
    Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))
    OutlinedTextField(
        value = pin,
        onValueChange = { pin = it.filter(Char::isDigit).take(REGISTRATION_PIN_LENGTH) },
        singleLine = true,
        isError = error != null,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        label = { Text("6-digit PIN") },
    )
    error?.let {
        Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
        Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))
    SanchrButton(text = "Continue", onClick = {
        onComplete(pin)
    }, enabled = pin.length == REGISTRATION_PIN_LENGTH, modifier = Modifier.fillMaxWidth())
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        SanchrTextButton(onClick = onCancel) { Text("Cancel") }
    }
}
