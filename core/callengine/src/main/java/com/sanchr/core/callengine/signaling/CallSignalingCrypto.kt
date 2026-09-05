package com.sanchr.core.callengine.signaling

import com.sanchr.core.crypto.SignalSessionManager
import com.sanchr.proto.calling.DeviceCallOffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

class CallPayloadException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Seals and opens the SDP that travels through the signaling server, the
 * way iOS does it: a [SealedCallPayload] JSON, Signal-encrypted for one
 * specific peer device. Opening verifies two things a relay could
 * otherwise tamper with — the payload's age (replay) and that the DTLS
 * fingerprint inside the ciphertext is the one the SDP advertises (a
 * swapped SDP would hand the media path to a man in the middle).
 */
@Singleton
class CallSignalingCrypto internal constructor(
    private val signal: SignalSessionManager,
    private val nowEpochSeconds: () -> Double,
) {
    @Inject
    constructor(signal: SignalSessionManager) : this(signal, { System.currentTimeMillis() / MILLIS_PER_SECOND })

    /** One ciphertext per key-capable device of [recipientId]. */
    suspend fun sealOffer(
        sdp: String,
        recipientId: String,
    ): List<DeviceCallOffer> {
        val payload = payloadFor(sdp, type = null)
        return signal.encryptCallOffers(payload.encode(), recipientId).map { DeviceCallOffer(it.deviceId, it.ciphertext) }
    }

    /** An answer (or, for a video upgrade, an offer) for the one device the call is with. */
    suspend fun sealAnswer(
        sdp: String,
        type: String,
        recipientId: String,
        deviceId: Int,
    ): ByteArray = signal.encryptForCall(payloadFor(sdp, type).encode(), recipientId, resolveDevice(deviceId))

    /** @throws CallPayloadException when the payload cannot be decrypted, is stale, or its fingerprint does not match its SDP. */
    suspend fun open(
        ciphertext: ByteArray,
        senderId: String,
        senderDevice: Int,
        maxAgeSeconds: Double,
    ): SealedCallPayload {
        val payload = decode(ciphertext, senderId, senderDevice)
        validate(payload, maxAgeSeconds)
        return payload
    }

    private suspend fun decode(
        ciphertext: ByteArray,
        senderId: String,
        senderDevice: Int,
    ): SealedCallPayload {
        if (ciphertext.isEmpty()) throw CallPayloadException("empty call payload")
        return try {
            SealedCallPayload.decode(signal.decryptForCall(ciphertext, senderId, resolveDevice(senderDevice)))
        } catch (e: Exception) {
            throw CallPayloadException("call payload did not decrypt or is not a SealedCallPayload", e)
        }
    }

    private fun validate(
        payload: SealedCallPayload,
        maxAgeSeconds: Double,
    ) {
        val age = abs(nowEpochSeconds() - payload.timestamp)
        if (age > maxAgeSeconds) throw CallPayloadException("stale call payload: age=${age.toInt()}s")
        val advertised = SealedCallPayload.fingerprintOf(payload.sdp)
        if (advertised == null || advertised != payload.dtlsFingerprint) {
            throw CallPayloadException("DTLS fingerprint missing or mismatched")
        }
    }

    private fun payloadFor(
        sdp: String,
        type: String?,
    ): SealedCallPayload {
        val fingerprint = SealedCallPayload.fingerprintOf(sdp) ?: throw CallPayloadException("local SDP has no DTLS fingerprint")
        return SealedCallPayload(sdp = sdp, type = type, dtlsFingerprint = fingerprint, timestamp = nowEpochSeconds())
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1000.0

        /** A zero/absent device id means "assume device 1", as iOS does for pre-multi-device servers. */
        fun resolveDevice(raw: Int): Int = if (raw > 0) raw else 1
    }
}
