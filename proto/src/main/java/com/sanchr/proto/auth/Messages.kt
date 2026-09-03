package com.sanchr.proto.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Handwritten mirrors of `sanchr.auth.*` proto messages. Kept as a thin
 * adapter layer so feature modules don't import `com.google.protobuf.*`
 * directly. Mirrors the proto field set 1:1 — do not add fields the
 * server does not send.
 */
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

/**
 * Phone-only OTP request — see `backend/crates/sanchr-core/src/auth/handlers.rs`
 * `handle_request_otp` (commit `bdfb5a7`). Replaces the previous
 * Register-with-placeholder workaround on the login entry path.
 */
@Serializable
data class RequestOtpRequest(
    @SerialName("phone_number") val phoneNumber: String = "",
    val device: DeviceInfo? = null,
)

/**
 * @property expiresInSeconds Seconds until the issued OTP expires.
 * @property existingUser True if the phone is already a verified user
 *   (the OTP was issued for login); false if a pending registration was
 *   created/refreshed.
 */
@Serializable
data class RequestOtpResponse(
    @SerialName("expires_in_seconds") val expiresInSeconds: Long = 0L,
    @SerialName("existing_user") val existingUser: Boolean = false,
)

@Serializable
data class VerifyOTPRequest(
    @SerialName("phone_number") val phoneNumber: String = "",
    @SerialName("otp_code") val otpCode: String = "",
    val device: DeviceInfo? = null,
    @SerialName("registration_lock_pin") val registrationLockPin: String = "",
)

@Serializable
data class LoginRequest(
    @SerialName("phone_number") val phoneNumber: String = "",
    val password: String = "",
    val device: DeviceInfo? = null,
    @SerialName("registration_lock_pin") val registrationLockPin: String = "",
)

@Serializable
data class RefreshTokenRequest(
    @SerialName("refresh_token") val refreshToken: String = "",
)

@Serializable
data class LogoutRequest(
    @SerialName("refresh_token") val refreshToken: String = "",
)

/**
 * Server AuthResponse. The proto message does not carry an explicit
 * `expires_in` field — [expiresIn] is derived at the mapping layer by
 * decoding the `exp` claim from [accessToken] (JWT). It falls back to `0`
 * when the token cannot be parsed; callers should treat `0` as "unknown,
 * refresh immediately".
 */
@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    val user: User? = null,
    @SerialName("device_id") val deviceId: Int = 0,
    @SerialName("expires_in") val expiresIn: Long = 0L,
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
    val email: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("status_text") val statusText: String = "",
    @SerialName("created_at") val createdAt: String = "",
)
