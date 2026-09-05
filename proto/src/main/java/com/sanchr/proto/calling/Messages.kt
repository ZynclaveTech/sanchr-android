package com.sanchr.proto.calling

/**
 * Wire models for `sanchr.calling.CallSignalingService` (calling.proto).
 *
 * SDP never travels in the clear: an offer or answer is a `SealedCallPayload`
 * JSON, Signal-encrypted for one specific peer device (see
 * `core:callengine`). The server only relays bytes.
 */
data class CallOffer(
    val recipientId: String,
    /** "voice" or "video". */
    val callType: String,
    /** One ciphertext per recipient device; the server fans each out to its device. */
    val deviceOffers: List<DeviceCallOffer>,
) {
    /**
     * The deprecated single-device field, which the server uses only when
     * [deviceOffers] is empty (addressed to device 1). iOS fills it with the
     * device-1 entry (or the lowest device) for older servers; so do we.
     */
    val legacyEncryptedSdpPayload: ByteArray
        get() =
            (deviceOffers.firstOrNull { it.deviceId == 1 } ?: deviceOffers.minByOrNull { it.deviceId })
                ?.encryptedSdpPayload
                ?: ByteArray(0)
}

class DeviceCallOffer(
    val deviceId: Int,
    /** Signal-encrypt(SealedCallPayload JSON) for this device, iOS-framed (type byte first). */
    val encryptedSdpPayload: ByteArray,
)

data class CallResponse(
    val callId: String,
    /** "ringing", "busy" or "unavailable". */
    val status: String,
)

/** One frame on the bidirectional `CallStream`. */
data class CallSignal(
    val callId: String,
    /**
     * Device id of the peer whose message this carries. Senders stamp their
     * own device id; for an encrypted answer the server sets it from the
     * callee's `CallJoin.answererDevice`. Zero means "unknown, assume 1".
     */
    val peerDevice: Int,
    val payload: CallSignalPayload,
)

sealed interface CallSignalPayload {
    /** JSON `{"candidate","sdpMid","sdpMLineIndex"}` — see `IceCandidateCodec`. */
    class IceCandidate(
        val json: ByteArray,
    ) : CallSignalPayload

    /** "ringing", "accepted", "declined", "busy", "ended", "cancelled", "missed", "failed", "ping", "muted", ... */
    data class Control(
        val action: String,
    ) : CallSignalPayload

    /** Signal-encrypt(SealedCallPayload JSON), iOS-framed. */
    class EncryptedSdpAnswer(
        val ciphertext: ByteArray,
    ) : CallSignalPayload

    /** Must be the first frame a client sends; consumed by the server, never relayed. */
    data class Join(
        /** "caller" or "callee". */
        val role: String,
        /** The callee's own device id, so the caller can address its answer. Zero for the caller. */
        val answererDevice: Int = 0,
    ) : CallSignalPayload
}

data class EndCallRequest(
    val callId: String,
    /** "ended", "declined", "cancelled", "busy", "missed" or "failed". */
    val reason: String,
)

data object EndCallResponse

data class GetCallHistoryRequest(
    val limit: Int = 50,
)

data class GetCallHistoryResponse(
    val entries: List<CallLogEntry>,
)

data class CallLogEntry(
    val callId: String,
    val peerId: String,
    /** Server-side plaintext; display goes through `ContactDisplayName`, never this. */
    val peerName: String,
    /** "voice" or "video". */
    val callType: String,
    /** "incoming" or "outgoing". */
    val direction: String,
    /** "completed", "missed", "declined" or "busy". */
    val status: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationSecs: Int,
)

data object GetTurnCredentialsRequest

data class TurnCredentials(
    val urls: List<String>,
    val username: String,
    val credential: String,
    val ttlSecs: Long,
)
