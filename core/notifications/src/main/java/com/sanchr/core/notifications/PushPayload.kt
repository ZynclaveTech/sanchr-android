package com.sanchr.core.notifications

import com.sanchr.core.common.calls.IncomingCallPush

/**
 * Wake-only FCM push payload.
 *
 * Phase C enforces the M3 metadata-privacy rule that FCM data payloads must
 * carry **no** message content, sender identity, conversation id, or badge
 * count — every inbound push is a bare "wake up and drain your Subscribe
 * stream" signal. Notifications are rendered later by [NewMessageNotifier]
 * from the decrypted local DB row, never from FCM data.
 *
 * The one exception is an incoming call (`hint=call`): the server sends the
 * call id, the caller and the call type — exactly what the APNs VoIP push
 * carries — so the phone can ring before the message stream is back up.
 * The SDP never travels by push.
 */
data class PushPayload(
    val type: String,
    val hint: String? = null,
    val call: IncomingCallPush? = null,
) {
    val isCall: Boolean get() = hint == HINT_CALL && call != null

    companion object {
        const val TYPE_WAKE = "wake"
        const val HINT_CALL = "call"
        private const val KEY_TYPE = "type"
        private const val KEY_HINT = "hint"
        private const val KEY_CALL_ID = "call_id"
        private const val KEY_CALLER_ID = "caller_id"
        private const val KEY_CALL_TYPE = "call_type"
        private const val KEY_CALLER_DEVICE = "caller_device"

        /**
         * Builds a [PushPayload] from an FCM data map, or returns `null` if
         * the payload lacks a `type` field (malformed / legacy). A call hint
         * without a call id is treated as a plain wake.
         */
        fun fromData(data: Map<String, String>): PushPayload? {
            val type = data[KEY_TYPE] ?: return null
            val hint = data[KEY_HINT]
            val call =
                if (hint == HINT_CALL) {
                    val callId = data[KEY_CALL_ID].orEmpty()
                    val callerId = data[KEY_CALLER_ID].orEmpty()
                    if (callId.isNotEmpty() && callerId.isNotEmpty()) {
                        IncomingCallPush(
                            callId = callId,
                            callerId = callerId,
                            callType = data[KEY_CALL_TYPE].orEmpty().ifEmpty { "voice" },
                            callerDevice = data[KEY_CALLER_DEVICE]?.toIntOrNull() ?: 0,
                        )
                    } else {
                        null
                    }
                } else {
                    null
                }
            return PushPayload(type = type, hint = hint, call = call)
        }
    }
}
