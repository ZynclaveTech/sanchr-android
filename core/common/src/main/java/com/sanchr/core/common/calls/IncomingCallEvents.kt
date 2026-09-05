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
 * The FCM wake for an incoming call: `hint=call` plus the call's identity,
 * never the SDP (that arrives as a `CallOfferEvent` once the message
 * stream reconnects). Same fields as the APNs VoIP push.
 */
data class IncomingCallPush(
    val callId: String,
    val callerId: String,
    /** "voice" or "video". */
    val callType: String,
    val callerDevice: Int,
)

/**
 * Where the realtime stream and the push service hand call events.
 * Implemented by the call engine; declared here so `sync` and
 * `core:notifications` do not depend on WebRTC.
 */
interface IncomingCallEvents {
    /** A call is ringing for us but its offer has not arrived yet: start ringing now, fill the SDP when [onCallOffer] comes. */
    suspend fun onCallPush(push: IncomingCallPush)

    suspend fun onCallOffer(offer: IncomingCallOffer)

    suspend fun onCallLifecycle(event: CallLifecycleSignal)
}

/**
 * Opens the message stream from the background for long enough to receive
 * what a push announced. Implemented by the realtime manager in `sync`.
 */
interface StreamWaker {
    fun wakeForCall(callId: String)
}

/** What to call a peer on the call screen; resolved by the app's contact layer. */
fun interface CallPeerNames {
    suspend fun displayNameFor(userId: String): String
}
