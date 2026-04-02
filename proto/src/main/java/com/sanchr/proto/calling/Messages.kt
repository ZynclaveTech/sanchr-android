package com.sanchr.proto.calling

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CallOffer(
    @SerialName("call_id") val callId: String = "",
    @SerialName("caller_id") val callerId: String = "",
    @SerialName("callee_id") val calleeId: String = "",
    @SerialName("call_type") val callType: String = "audio",
    @SerialName("sdp_offer") val sdpOffer: String = "",
    val timestamp: Long = 0L,
)

@Serializable
data class CallResponse(
    @SerialName("call_id") val callId: String = "",
    val accepted: Boolean = false,
    @SerialName("sdp_answer") val sdpAnswer: String = "",
)

@Serializable
sealed interface CallSignal {

    @Serializable
    @SerialName("sdp_answer")
    data class SdpAnswer(
        @SerialName("call_id") val callId: String = "",
        val sdp: String = "",
    ) : CallSignal

    @Serializable
    @SerialName("ice_candidate")
    data class IceCandidate(
        @SerialName("call_id") val callId: String = "",
        val candidate: String = "",
        @SerialName("sdp_mid") val sdpMid: String = "",
        @SerialName("sdp_m_line_index") val sdpMLineIndex: Int = 0,
    ) : CallSignal

    @Serializable
    @SerialName("control")
    data class Control(
        @SerialName("call_id") val callId: String = "",
        val action: String = "",
    ) : CallSignal
}

@Serializable
data class CallControl(
    @SerialName("call_id") val callId: String = "",
    val action: String = "",
    val reason: String = "",
)

@Serializable
data class EndCallRequest(
    @SerialName("call_id") val callId: String = "",
    val reason: String = "",
)

@Serializable
data class EndCallResponse(
    val success: Boolean = false,
    @SerialName("duration_seconds") val durationSeconds: Int = 0,
)

@Serializable
data class GetCallHistoryRequest(
    @SerialName("page_token") val pageToken: String = "",
    @SerialName("page_size") val pageSize: Int = 50,
)

@Serializable
data class GetCallHistoryResponse(
    val entries: List<CallLogEntry> = emptyList(),
    @SerialName("next_page_token") val nextPageToken: String = "",
)

@Serializable
data class CallLogEntry(
    @SerialName("call_id") val callId: String = "",
    @SerialName("caller_id") val callerId: String = "",
    @SerialName("callee_id") val calleeId: String = "",
    @SerialName("call_type") val callType: String = "",
    val status: String = "",
    @SerialName("started_at") val startedAt: Long = 0L,
    @SerialName("ended_at") val endedAt: Long = 0L,
    @SerialName("duration_seconds") val durationSeconds: Int = 0,
)

@Serializable
data class GetTurnCredentialsRequest(
    @SerialName("call_id") val callId: String = "",
)

@Serializable
data class TurnCredentials(
    val urls: List<String> = emptyList(),
    val username: String = "",
    val credential: String = "",
    @SerialName("ttl_seconds") val ttlSeconds: Int = 0,
)
