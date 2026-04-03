package com.sanchr.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.work.WorkManager
import com.sanchr.core.designsystem.theme.SanchrTheme
import com.sanchr.core.notifications.NotificationHandler
import com.sanchr.sync.SyncState
import com.sanchr.sync.SyncWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var notificationHandler: NotificationHandler

    @Inject
    lateinit var syncState: SyncState

    /**
     * Launcher for the POST_NOTIFICATIONS runtime permission dialog (Android 13+).
     * The result is intentionally ignored -- if the user denies the permission,
     * notifications simply will not appear and the app continues to function.
     */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* no-op: respect user choice */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // TODO: Keep splash screen visible while checking auth state
        // splashScreen.setKeepOnScreenCondition { !viewModel.isReady.value }

        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        handleNotificationIntent(intent)

        setContent {
            SanchrTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SanchrNavHost()
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
     */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    // ------------------------------------------------------------------
    // Notification permission (Android 13+ / API 33)
    // ------------------------------------------------------------------

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
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
     * taps a notification. Navigates to the appropriate screen.
     *
     * Extras handled:
     * - `conversationId` -- opens the chat detail screen
     * - `call_id` + `call_action` -- opens the call screen
     */
    private fun handleNotificationIntent(intent: android.content.Intent?) {
        if (intent == null) return

        val conversationId = intent.getStringExtra("conversationId")
        if (conversationId != null) {
            // Clear notifications for this conversation since the user is navigating to it
            notificationHandler.cancelNotificationsForConversation(conversationId)
            // TODO: Navigate to conversation via NavController deep link
            // navController.navigate("chat_detail/$conversationId")
        }

        val callId = intent.getStringExtra("call_id")
        val callAction = intent.getStringExtra("call_action")
        if (callId != null) {
            // TODO: Navigate to call screen
            // navController.navigate("call/$callId?action=$callAction")
        }
    }
}
