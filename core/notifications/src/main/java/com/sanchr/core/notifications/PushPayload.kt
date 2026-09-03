package com.sanchr.core.notifications

/**
 * Wake-only FCM push payload.
 *
 * Phase C enforces the M3 metadata-privacy rule that FCM data payloads must
 * carry **no** message content, sender identity, conversation id, or badge
 * count — every inbound push is a bare "wake up and drain your Subscribe
 * stream" signal. Notifications are rendered later by [NewMessageNotifier]
 * from the decrypted local DB row, never from FCM data.
 *
 * Only [type] and an optional [hint] (opaque, server-chosen; e.g. "message"
 * vs "call" — used purely for drain prioritisation, never displayed) are
 * consumed by the app. Any other field on the FCM payload is ignored.
 */
data class PushPayload(
    val type: String,
    val hint: String? = null,
) {
    companion object {
        const val TYPE_WAKE = "wake"

        private const val KEY_TYPE = "type"
        private const val KEY_HINT = "hint"

        /**
         * Builds a [PushPayload] from an FCM data map, or returns `null` if
         * the payload lacks a `type` field (malformed / legacy).
         */
        fun fromData(data: Map<String, String>): PushPayload? =
            data[KEY_TYPE]?.let { type -> PushPayload(type = type, hint = data[KEY_HINT]) }
    }
}
