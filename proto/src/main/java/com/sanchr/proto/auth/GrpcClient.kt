package com.sanchr.proto.auth

import io.grpc.CallOptions
import io.grpc.Channel
import sanchr.auth.Auth
import sanchr.auth.AuthServiceGrpcKt

/**
 * gRPC client interface for the AuthService.
 * Adapter over the generated `sanchr.auth.AuthServiceGrpcKt.AuthServiceCoroutineStub`.
 *
 * DeleteAccount and RequestChallenge are intentionally omitted — the Android
 * client does not drive account deletion (M6+) or proof-of-work challenges
 * (server accepts empty proof in v1).
 */
interface AuthServiceClient {
    suspend fun register(request: RegisterRequest): AuthResponse

    suspend fun verifyOtp(request: VerifyOTPRequest): AuthResponse

    suspend fun login(request: LoginRequest): AuthResponse

    suspend fun refreshToken(request: RefreshTokenRequest): AuthResponse

    suspend fun logout(request: LogoutRequest): LogoutResponse
}

class AuthServiceGrpcClient(
    private val channel: Channel,
    private val callOptions: CallOptions = CallOptions.DEFAULT,
) : AuthServiceClient {
    private val stub by lazy { AuthServiceGrpcKt.AuthServiceCoroutineStub(channel, callOptions) }

    override suspend fun register(request: RegisterRequest): AuthResponse = stub.register(request.toProto()).toManual()

    override suspend fun verifyOtp(request: VerifyOTPRequest): AuthResponse = stub.verifyOTP(request.toProto()).toManual()

    override suspend fun login(request: LoginRequest): AuthResponse = stub.login(request.toProto()).toManual()

    override suspend fun refreshToken(request: RefreshTokenRequest): AuthResponse = stub.refreshToken(request.toProto()).toManual()

    override suspend fun logout(request: LogoutRequest): LogoutResponse {
        stub.logout(request.toProto())
        return LogoutResponse(success = true)
    }
}

// region ── Request mappers ────────────────────────────────────────────────

private fun DeviceInfo.toProto(): Auth.DeviceInfo =
    Auth.DeviceInfo
        .newBuilder()
        .setDeviceName(deviceName)
        .setPlatform(platform)
        .setInstallationId(installationId)
        .setSupportsDeliveryAck(supportsDeliveryAck)
        .build()

private fun RegisterRequest.toProto(): Auth.RegisterRequest {
    val builder =
        Auth.RegisterRequest
            .newBuilder()
            .setPhoneNumber(phoneNumber)
            .setDisplayName(displayName)
            .setPassword(password)
            .setEmail(email)
    // challenge_proof intentionally left unset — Android does not
    // perform proof-of-work; server accepts empty in v1.
    device?.let { builder.device = it.toProto() }
    return builder.build()
}

private fun VerifyOTPRequest.toProto(): Auth.VerifyOTPRequest {
    val builder =
        Auth.VerifyOTPRequest
            .newBuilder()
            .setPhoneNumber(phoneNumber)
            .setOtpCode(otpCode)
            .setRegistrationLockPin(registrationLockPin)
    device?.let { builder.device = it.toProto() }
    return builder.build()
}

private fun LoginRequest.toProto(): Auth.LoginRequest {
    val builder =
        Auth.LoginRequest
            .newBuilder()
            .setPhoneNumber(phoneNumber)
            .setPassword(password)
            .setRegistrationLockPin(registrationLockPin)
    device?.let { builder.device = it.toProto() }
    return builder.build()
}

private fun RefreshTokenRequest.toProto(): Auth.RefreshTokenRequest =
    Auth.RefreshTokenRequest
        .newBuilder()
        .setRefreshToken(refreshToken)
        .build()

private fun LogoutRequest.toProto(): Auth.LogoutRequest =
    Auth.LogoutRequest
        .newBuilder()
        .setRefreshToken(refreshToken)
        .build()

// endregion

// region ── Response mappers ───────────────────────────────────────────────

private fun Auth.User.toManual(): User =
    User(
        id = id,
        phoneNumber = phoneNumber,
        displayName = displayName,
        email = email,
        avatarUrl = avatarUrl,
        statusText = statusText,
        createdAt = createdAt,
    )

private fun Auth.AuthResponse.toManual(): AuthResponse =
    AuthResponse(
        accessToken = accessToken,
        refreshToken = refreshToken,
        user = if (hasUser()) user.toManual() else null,
        deviceId = deviceId,
        expiresIn = JwtExpiryExtractor.extractExpiresInSeconds(accessToken),
    )

// endregion

/**
 * Extracts the `exp` claim (seconds-since-epoch) from a JWT access token
 * and converts it to a relative seconds-until-expiry value. Returns 0 when
 * the token is empty, malformed, or already expired — callers should treat
 * 0 as "refresh on next use".
 *
 * Pure Kotlin: uses `java.util.Base64` URL-safe decoder on the payload
 * segment and a minimal regex to read `"exp":<number>`. We intentionally
 * avoid pulling in a full JSON parser for three fields we never round-trip.
 */
private object JwtExpiryExtractor {
    private val EXP_CLAIM = Regex("\"exp\"\\s*:\\s*(\\d+)")

    fun extractExpiresInSeconds(accessToken: String): Long {
        if (accessToken.isEmpty()) return 0L
        val segments = accessToken.split('.')
        if (segments.size < 2) return 0L
        return try {
            val payload =
                java.util.Base64
                    .getUrlDecoder()
                    .decode(padBase64(segments[1]))
            val json = String(payload, Charsets.UTF_8)
            val expSeconds =
                EXP_CLAIM
                    .find(json)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toLongOrNull() ?: return 0L
            val nowSeconds = System.currentTimeMillis() / 1_000L
            (expSeconds - nowSeconds).coerceAtLeast(0L)
        } catch (_: IllegalArgumentException) {
            0L
        } catch (_: IndexOutOfBoundsException) {
            0L
        }
    }

    private fun padBase64(segment: String): String {
        val mod = segment.length % 4
        return if (mod == 0) segment else segment + "=".repeat(4 - mod)
    }
}
