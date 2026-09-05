package com.sanchr.core.common.calls

/**
 * A call offer as delivered on the message stream (`CallOfferEvent`).
 * [encryptedSdpPayload] is the caller's Signal-encrypted `SealedCallPayload`
 * for this device; [callerDevice] addresses the session it came from
 * (zero means "unknown, assume 1").
 */
class IncomingCallOffer(
    val callId: String,
    val callerId: String,
    /** "voice" or "video". */
    val callType: String,
    val encryptedSdpPayload: ByteArray,
    val callerDevice: Int,
)

/** A `CallLifecycleEvent`: the server telling us how a call changed state. */
data class CallLifecycleSignal(
    val callId: String,
    val peerId: String,
    /** "ringing", "accepted", "declined", "busy", "ended", "cancelled", "missed" or "failed". */
    val eventType: String,
)

/**
 * Where the realtime stream hands call events. Implemented by the call
 * engine; declared here so `sync` does not depend on WebRTC.
 */
interface IncomingCallEvents {
    suspend fun onCallOffer(offer: IncomingCallOffer)

    suspend fun onCallLifecycle(event: CallLifecycleSignal)
}

/** What to call a peer on the call screen; resolved by the app's contact layer. */
fun interface CallPeerNames {
    suspend fun displayNameFor(userId: String): String
}
