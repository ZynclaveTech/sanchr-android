package com.sanchr.proto.notifications

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterPushTokenRequest(
    val token: String = "",
    val platform: String = "ANDROID",
    @SerialName("device_id") val deviceId: String = "",
)

@Serializable
data class RegisterPushTokenResponse(
    val success: Boolean = false,
)

@Serializable
data class UpdateNotificationPrefsRequest(
    @SerialName("messages_enabled") val messagesEnabled: Boolean = true,
    @SerialName("calls_enabled") val callsEnabled: Boolean = true,
    @SerialName("show_preview") val showPreview: Boolean = true,
    @SerialName("sound_enabled") val soundEnabled: Boolean = true,
    @SerialName("vibration_enabled") val vibrationEnabled: Boolean = true,
    @SerialName("quiet_hours_start") val quietHoursStart: String = "",
    @SerialName("quiet_hours_end") val quietHoursEnd: String = "",
)

@Serializable
data class UpdateNotificationPrefsResponse(
    val success: Boolean = false,
)
