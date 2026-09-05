package com.sanchr.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.WorkManager
import com.sanchr.app.bootstrap.AppBootstrapViewModel
import com.sanchr.app.bootstrap.StartDestination
import com.sanchr.app.lock.AppLockGate
import com.sanchr.app.navigation.PendingDestination
import com.sanchr.core.common.lock.AppLockPolicy
import com.sanchr.core.crypto.SignalKeyManager
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.datastore.UserPreferences
import com.sanchr.core.designsystem.theme.SanchrTheme
import com.sanchr.core.designsystem.theme.ThemeMode
import com.sanchr.core.notifications.NotificationHandler
import com.sanchr.sync.SyncState
import com.sanchr.sync.SyncWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var notificationHandler: NotificationHandler

    @Inject
    lateinit var syncState: SyncState

    @Inject
    lateinit var signalKeyManager: SignalKeyManager

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var userPreferences: UserPreferences

    private val bootstrapViewModel: AppBootstrapViewModel by viewModels()

    /**
     * Launcher for the POST_NOTIFICATIONS runtime permission dialog (Android 13+).
     * The result is intentionally ignored -- if the user denies the permission,
     * notifications simply will not appear and the app continues to function.
     */
    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { /* no-op: respect user choice */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Keep splash visible until the bootstrap VM resolves auth vs main, so we
        // don't flash a loading spinner before the first real frame.
        splashScreen.setKeepOnScreenCondition {
            bootstrapViewModel.startDestination.value is StartDestination.Loading
        }

        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        handleNotificationIntent(intent)
        bootstrapSignalKeysWhenAuthenticated()
        observeScreenshotProtectionPreference()

        setContent {
            val themeMode by userPreferences.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            SanchrTheme(darkTheme = ThemeMode.resolveDarkTheme(themeMode, isSystemInDarkTheme())) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val screenLockEnabled by userPreferences.screenLockEnabled.collectAsStateWithLifecycle(initialValue = false)
                    val biometricEnabled by userPreferences.biometricEnabled.collectAsStateWithLifecycle(initialValue = false)
                    val timeoutSeconds by userPreferences.screenLockTimeoutSeconds.collectAsStateWithLifecycle(initialValue = 0)
                    val configured = AppLockPolicy.isConfigured(screenLockEnabled, biometricEnabled)

                    // Cold launch with the lock on starts locked; the preferences
                    // arrive a frame later, so arm it as soon as they say so.
                    var locked by rememberSaveable { mutableStateOf(false) }
                    var everConfigured by rememberSaveable { mutableStateOf(false) }
                    var backgroundedAt by rememberSaveable { mutableStateOf<Long?>(null) }
                    LaunchedEffect(configured) {
                        if (configured && !everConfigured) {
                            everConfigured = true
                            locked = true
                        }
                        if (!configured) locked = false
                    }

                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner, configured, timeoutSeconds) {
                        val observer =
                            LifecycleEventObserver { _, event ->
                                when (event) {
                                    Lifecycle.Event.ON_STOP -> backgroundedAt = System.currentTimeMillis()
                                    Lifecycle.Event.ON_START -> {
                                        if (AppLockPolicy.shouldLockOnResume(
                                                configured,
                                                backgroundedAt,
                                                System.currentTimeMillis(),
                                                timeoutSeconds,
                                            )
                                        ) {
                                            locked = true
                                        }
                                        backgroundedAt = null
                                    }
                                    else -> Unit
                                }
                            }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    if (locked) {
                        // The nav host is not composed while locked, so no chat is
                        // behind this and none reaches the recents thumbnail.
                        AppLockGate(onUnlocked = { locked = false })
                    } else {
                        SanchrNavHost(
                            pendingDestination = bootstrapViewModel.pendingDestination,
                            onPendingConsumed = { bootstrapViewModel.setPendingDestination(null) },
                        )
                    }
                }
            }
        }
    }

    /**
     * Runs the Signal key bootstrap exactly once, and only after
     * [AppBootstrapViewModel] has confirmed the app is starting in the Main
     * destination (i.e. a token AND an identity private key are persisted).
     *
     * This avoids a race for half-registered accounts where the legacy
     * "got a token but no identity key yet" path would otherwise fire
     * `generateIdentity()` / `uploadInitialKeyBundle()` in parallel with the
     * auth flow rebuilding them.
     */
    private fun bootstrapSignalKeysWhenAuthenticated() {
        lifecycleScope.launch {
            bootstrapViewModel.startDestination
                .filterIsInstance<StartDestination.Main>()
                .first()

            val userId = sessionManager.getUserId() ?: return@launch
            val deviceId = sessionManager.getDeviceId()?.toIntOrNull() ?: return@launch

            runCatching {
                if (!signalKeyManager.hasIdentity()) {
                    signalKeyManager.generateIdentity()
                    signalKeyManager.uploadInitialKeyBundle()
                } else if (!signalKeyManager.hasCompleteServerBundle(userId, deviceId)) {
                    signalKeyManager.uploadInitialKeyBundle()
                } else {
                    signalKeyManager.checkAndReplenishPreKeys()
                    signalKeyManager.rotateSignedPreKeyIfNeeded()
                }
            }
        }
    }

    /**
     * Reactively applies `WindowManager.LayoutParams.FLAG_SECURE` to this
     * activity's window whenever the user's global screenshot-protection
     * preference is enabled, and clears it when disabled.
     *
     * Scoped to `Lifecycle.State.STARTED` via [repeatOnLifecycle] so the
     * collector is torn down on stop and re-established on start; this avoids
     * leaking the collector across configuration changes and backgrounding.
     * FLAG_SECURE itself is a window attribute — it survives configuration
     * changes because the `Window` is recreated per-Activity-instance and the
     * preference is re-applied as soon as the new instance restarts.
     *
     * Note: this applies to the *entire activity window*. Individual sensitive
     * composables (OTP, recovery-key) still force-enable FLAG_SECURE via
     * `SecureScreen()` regardless of this preference. The `SecureScreen`
     * disposable is careful to only clear the flag on dispose if it was not
     * already set — so when the global toggle is on, leaving an OTP screen
     * never accidentally drops protection on the rest of the app.
     */
    private fun observeScreenshotProtectionPreference() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userPreferences.screenshotProtectionEnabled.collect { enabled ->
                    if (enabled) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE,
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Trigger a sync if data is stale (>5 min since last sync)
        if (syncState.needsSync) {
            SyncWorker.syncNow(WorkManager.getInstance(this))
        }
    }

    /**
     * Called when the activity receives a new intent while already running
     * (e.g., user taps a notification while the app is in the foreground).
     * Requires `android:launchMode="singleTop"` on this activity in the
     * manifest -- without it, a notification tap while running would create
     * a new activity instance instead of reaching this callback.
     */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    // ------------------------------------------------------------------
    // Notification permission (Android 13+ / API 33)
    // ------------------------------------------------------------------

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ------------------------------------------------------------------
    // Deep-link from notification tap
    // ------------------------------------------------------------------

    /**
     * Inspects the intent extras set by [NotificationHandler] when the user
     * taps a notification and records the destination as pending. The nav
     * host delivers it once the session is confirmed active (see
     * [SanchrNavHost]) so a tap arriving before sign-in, or while the app is
     * still resolving its start destination, never lands behind the auth flow.
     *
     * Extras handled:
     * - `conversationId` -- opens the chat detail screen
     * - `call_id` + `call_action` -- opens the call screen
     */
    private fun handleNotificationIntent(intent: android.content.Intent?) {
        val destination = PendingDestination.fromIntent(intent) ?: return
        if (destination is PendingDestination.Conversation) {
            // Clear notifications for this conversation since the user is navigating to it
            notificationHandler.cancelNotificationsForConversation(destination.id)
        }
        bootstrapViewModel.setPendingDestination(destination)
    }
}
