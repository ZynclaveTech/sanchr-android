package com.sanchr.proto.notifications

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Handwritten mirrors of `sanchr.notifications.*` proto messages. Kept as
 * a thin adapter so feature modules don't import `com.google.protobuf.*`
 * directly. Mirrors the proto field set 1:1.
 */
@Serializable
data class RegisterPushTokenRequest(
    val token: String = "",
    val platform: String = "android",
    @SerialName("voip_token") val voipToken: String = "",
)

@Serializable
data class RegisterPushTokenResponse(
    val success: Boolean = false,
)

@Serializable
data class UpdateNotificationPrefsRequest(
    @SerialName("message_notifications") val messageNotifications: Boolean = true,
    @SerialName("group_notifications") val groupNotifications: Boolean = true,
    @SerialName("call_notifications") val callNotifications: Boolean = true,
    @SerialName("notification_sound") val notificationSound: String = "",
    val vibrate: Boolean = true,
    @SerialName("show_preview") val showPreview: Boolean = true,
)

@Serializable
data class UpdateNotificationPrefsResponse(
    val success: Boolean = false,
)
