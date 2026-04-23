package com.sanchr.core.callengine

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CallService : Service() {
    companion object {
        private const val TAG = "CallService"

        const val ACTION_START_CALL = "com.sanchr.callengine.ACTION_START_CALL"
        const val ACTION_INCOMING_CALL = "com.sanchr.callengine.ACTION_INCOMING_CALL"
        const val ACTION_ANSWER = "com.sanchr.callengine.ACTION_ANSWER"
        const val ACTION_DECLINE = "com.sanchr.callengine.ACTION_DECLINE"
        const val ACTION_END_CALL = "com.sanchr.callengine.ACTION_END_CALL"

        const val EXTRA_RECIPIENT_ID = "extra_recipient_id"
        const val EXTRA_RECIPIENT_NAME = "extra_recipient_name"
        const val EXTRA_IS_VIDEO = "extra_is_video"
        const val EXTRA_CALL_ID = "extra_call_id"
        const val EXTRA_CALLER_ID = "extra_caller_id"
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_SDP_OFFER = "extra_sdp_offer"
    }

    @Inject lateinit var callManager: CallManager

    @Inject lateinit var callNotificationManager: CallNotificationManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var isServiceStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        observeCallState()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START_CALL -> handleStartCall(intent)
            ACTION_INCOMING_CALL -> handleIncomingCall(intent)
            ACTION_ANSWER -> handleAnswer()
            ACTION_DECLINE -> handleDecline()
            ACTION_END_CALL -> handleEndCall()
            else -> {
                Log.w(TAG, "Unknown action: ${intent?.action}")
            }
        }

        return START_NOT_STICKY
    }

    private fun handleStartCall(intent: Intent) {
        val recipientId = intent.getStringExtra(EXTRA_RECIPIENT_ID) ?: return
        val recipientName = intent.getStringExtra(EXTRA_RECIPIENT_NAME) ?: recipientId
        val isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)

        startForegroundWithNotification(recipientName, if (isVideo) "video" else "voice")

        serviceScope.launch {
            callManager.startCall(recipientId, recipientName, isVideo)
        }
    }

    private fun handleIncomingCall(intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return
        val callerId = intent.getStringExtra(EXTRA_CALLER_ID) ?: return
        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: callerId
        val sdpOffer = intent.getStringExtra(EXTRA_SDP_OFFER) ?: return
        val isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)

        val notification =
            callNotificationManager.showIncomingCallNotification(
                callerName = callerName,
                isVideo = isVideo,
                callId = callId,
            )

        startForegroundService(notification)

        callManager.handleIncomingCall(
            callId = callId,
            callerId = callerId,
            callerName = callerName,
            sdpOffer = sdpOffer,
            isVideo = isVideo,
        )
    }

    private fun handleAnswer() {
        serviceScope.launch {
            callManager.answerCall()
        }
    }

    private fun handleDecline() {
        callManager.declineCall()
        stopSelfAndCleanup()
    }

    private fun handleEndCall() {
        serviceScope.launch {
            callManager.endCall()
            stopSelfAndCleanup()
        }
    }

    private fun startForegroundWithNotification(
        callerName: String,
        callType: String,
    ) {
        val notification =
            callNotificationManager.showOngoingCallNotification(
                callerName = callerName,
                callType = callType,
                isIncoming = false,
            )
        startForegroundService(notification)
    }

    private fun startForegroundService(notification: android.app.Notification) {
        if (isServiceStarted) return
        isServiceStarted = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                CallNotificationManager.NOTIFICATION_ID_ONGOING,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
            )
        } else {
            startForeground(
                CallNotificationManager.NOTIFICATION_ID_ONGOING,
                notification,
            )
        }
    }

    private fun observeCallState() {
        serviceScope.launch {
            callManager.callState.collectLatest { state ->
                when (state) {
                    is CallState.Active -> {
                        val name = state.remoteUserName
                        val callType = callManager.callType.value
                        val notification =
                            callNotificationManager.showOngoingCallNotification(
                                callerName = name,
                                callType = callType,
                                isIncoming = false,
                            )
                        if (isServiceStarted) {
                            val nm =
                                getSystemService(NOTIFICATION_SERVICE)
                                    as android.app.NotificationManager
                            nm.notify(
                                CallNotificationManager.NOTIFICATION_ID_ONGOING,
                                notification,
                            )
                        }
                    }

                    is CallState.Ended -> {
                        stopSelfAndCleanup()
                    }

                    is CallState.Idle -> {
                        if (isServiceStarted) {
                            stopSelfAndCleanup()
                        }
                    }

                    else -> {
                        // Outgoing, Incoming, Ringing, Reconnecting -- keep foreground alive
                    }
                }
            }
        }
    }

    private fun stopSelfAndCleanup() {
        callNotificationManager.cancelCallNotification()
        isServiceStarted = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
