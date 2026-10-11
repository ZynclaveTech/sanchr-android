package com.sanchr.app.call

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.callengine.CallNotificationManager
import com.sanchr.core.datastore.UserPreferences
import com.sanchr.core.designsystem.theme.SanchrTheme
import com.sanchr.core.designsystem.theme.ThemeMode
import com.sanchr.feature.calls.ActiveCallScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The call screen for a call that is ringing or under way, in an activity of
 * its own.
 *
 * An incoming call had no screen at all. The notification's tap and its
 * full-screen intent were both a broadcast that nothing received, so a call
 * rang as a notification and nothing else: no ringing screen over the lock
 * screen, and after "Answer" no mute, no speaker, no way to hang up but the
 * notification.
 *
 * It is its own activity, not a destination inside [com.sanchr.app.MainActivity],
 * because it has to be shown over the lock screen, and an activity shown over
 * the lock screen shows everything it can navigate to. Here that is the call
 * and nothing else: there is no back stack of chats underneath, so answering a
 * call on a locked phone does not hand over the conversations on it. For the
 * same reason this does not go through App Lock — the caller's name is all it
 * shows, and a call that has to be unlocked before it can be answered is a
 * missed call.
 */
@AndroidEntryPoint
class CallActivity : ComponentActivity() {
    @Inject lateinit var userPreferences: UserPreferences

    /** Set by the notification's Answer action; consumed once by the screen. */
    private var answerRequested by mutableStateOf(false)
    private var callId by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showOverLockScreen()
        // A restored instance must not answer again a call the first one did.
        if (savedInstanceState == null) read(intent) else callId = savedInstanceState.getString(STATE_CALL_ID).orEmpty()

        setContent {
            val themeMode by userPreferences.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            SanchrTheme(darkTheme = ThemeMode.resolveDarkTheme(themeMode, isSystemInDarkTheme())) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ActiveCallScreen(
                        callId = callId,
                        onCallEnded = ::finish,
                        answerRequested = answerRequested,
                        onAnswerRequestHandled = { answerRequested = false },
                    )
                }
            }
        }
    }

    /** singleTop: a second tap on the notification, or Answer, lands here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        read(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CALL_ID, callId)
    }

    private fun read(intent: Intent?) {
        callId = intent?.getStringExtra(CallNotificationManager.EXTRA_CALL_ID).orEmpty()
        answerRequested = intent?.getStringExtra(CallNotificationManager.EXTRA_CALL_ACTION) == CallNotificationManager.CALL_ACTION_ANSWER
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        private const val STATE_CALL_ID = "call_id"

        /** Opens the call screen for the call in progress, from inside the app. */
        fun open(
            context: Context,
            callId: String,
        ) {
            context.startActivity(
                Intent(context, CallActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(CallNotificationManager.EXTRA_CALL_ID, callId),
            )
        }
    }
}
