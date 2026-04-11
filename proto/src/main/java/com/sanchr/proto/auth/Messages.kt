package com.sanchr.proto.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(
    @SerialName("device_name") val deviceName: String = "",
    val platform: String = "android",
    @SerialName("installation_id") val installationId: String = "",
    @SerialName("supports_delivery_ack") val supportsDeliveryAck: Boolean = false,
)

@Serializable
data class RegisterRequest(
    @SerialName("phone_number") val phoneNumber: String = "",
    @SerialName("display_name") val displayName: String = "",
    val password: String = "",
    val email: String = "",
    val device: DeviceInfo? = null,
)

@Serializable
data class VerifyOTPRequest(
    @SerialName("phone_number") val phoneNumber: String = "",
    val code: String = "",
    val device: DeviceInfo? = null,
)

@Serializable
data class LoginRequest(
    @SerialName("phone_number") val phoneNumber: String = "",
    val password: String = "",
    val device: DeviceInfo? = null,
)

@Serializable
data class RefreshTokenRequest(
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("device_id") val deviceId: String = "",
)

@Serializable
data class LogoutRequest(
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("all_devices") val allDevices: Boolean = false,
)

@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: Long = 0L,
    val user: User? = null,
    @SerialName("is_new_user") val isNewUser: Boolean = false,
    @SerialName("device_id") val deviceId: Int = 0,
)

@Serializable
data class LogoutResponse(
    val success: Boolean = false,
)

@Serializable
data class User(
    val id: String = "",
    @SerialName("phone_number") val phoneNumber: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    val bio: String = "",
    val email: String = "",
    @SerialName("created_at") val createdAt: Long = 0L,
    @SerialName("updated_at") val updatedAt: Long = 0L,
)
