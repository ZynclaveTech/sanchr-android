package com.sanchr.core.callengine.signaling

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.webrtc.IceCandidate

/**
 * `CallSignal.ice_candidate` bytes: the JSON iOS reads and writes
 * (`{"candidate","sdpMid","sdpMLineIndex"}`). Candidates are not encrypted
 * — they carry addresses the relay sees anyway — but they are opaque to
 * the server.
 */
object IceCandidateCodec {
    @Serializable
    private data class Wire(
        val candidate: String,
        val sdpMid: String? = null,
        @SerialName("sdpMLineIndex") val sdpMLineIndex: Int,
    )

    private val JSON =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    fun encode(candidate: IceCandidate): ByteArray =
        JSON.encodeToString(Wire(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)).toByteArray(Charsets.UTF_8)

    /** Null when the bytes are not a candidate; a malformed frame is dropped, not fatal. */
    fun decode(bytes: ByteArray): IceCandidate? =
        runCatching { JSON.decodeFromString(Wire.serializer(), String(bytes, Charsets.UTF_8)) }
            .getOrNull()
            ?.let { IceCandidate(it.sdpMid, it.sdpMLineIndex, it.candidate) }
}
