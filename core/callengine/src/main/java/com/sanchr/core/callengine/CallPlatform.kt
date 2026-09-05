package com.sanchr.core.callengine

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.sanchr.proto.calling.TurnCredentials
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.webrtc.PeerConnection

/** The Android-platform edges of a call: audio routing, ICE servers, the incoming-call service. */
@Singleton
class CallPlatform
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val audioManager: AudioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        fun configureAudioSession(isVideo: Boolean) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = isVideo
        }

        fun resetAudioSession() {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        }

        fun startIncomingCallService(
            callId: String,
            callerId: String,
            callerName: String,
            isVideo: Boolean,
        ) {
            val intent =
                Intent(context, CallService::class.java).apply {
                    action = CallService.ACTION_INCOMING_CALL
                    putExtra(CallService.EXTRA_CALL_ID, callId)
                    putExtra(CallService.EXTRA_CALLER_ID, callerId)
                    putExtra(CallService.EXTRA_CALLER_NAME, callerName)
                    putExtra(CallService.EXTRA_IS_VIDEO, isVideo)
                }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: IllegalStateException) {
                // Background start restrictions (API 31+). The in-app UI still
                // observes callState; only the system notification is lost.
                Log.w(TAG, "Could not start CallService for incoming call: ${e.message}")
            }
        }

        fun buildIceServers(creds: TurnCredentials): List<PeerConnection.IceServer> {
            val servers = mutableListOf<PeerConnection.IceServer>()
            if (creds.urls.isNotEmpty()) {
                servers +=
                    PeerConnection.IceServer
                        .builder(creds.urls)
                        .setUsername(creds.username)
                        .setPassword(creds.credential)
                        .createIceServer()
            }
            servers += PeerConnection.IceServer.builder(STUN_URL).createIceServer()
            return servers
        }

        private companion object {
            const val TAG = "CallPlatform"
            const val STUN_URL = "stun:stun.l.google.com:19302"
        }
    }
