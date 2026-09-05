package com.sanchr.core.notifications

import android.util.Log
import com.sanchr.core.common.calls.IncomingCallEvents
import com.sanchr.core.common.calls.StreamWaker
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a wake push makes the app do. A message wake drains the queue; a
 * call wake rings the call engine immediately and reopens the message
 * stream so the queued `CallOfferEvent` (the SDP) can be replayed to it.
 * Extracted from the FCM service so it can be tested.
 */
@Singleton
class PushRouter
    @Inject
    constructor(
        private val drainScheduler: MessageDrainScheduler,
        private val incomingCallEvents: IncomingCallEvents,
        private val streamWaker: StreamWaker,
    ) {
        suspend fun route(payload: PushPayload?) {
            if (payload == null || payload.type != PushPayload.TYPE_WAKE) {
                Log.w(TAG, "ignoring FCM push with unexpected type=${payload?.type ?: "<none>"}")
                return
            }
            val call = payload.call
            if (payload.isCall && call != null) {
                Log.i(TAG, "call wake for ${call.callId}")
                incomingCallEvents.onCallPush(call)
                streamWaker.wakeForCall(call.callId)
                return
            }
            drainScheduler.enqueueDrain()
        }

        private companion object {
            const val TAG = "PushRouter"
        }
    }
