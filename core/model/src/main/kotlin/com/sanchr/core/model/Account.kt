package com.sanchr.core.model

import kotlinx.serialization.Serializable

enum class RegistrationState { UNREGISTERED, OTP_PENDING, REGISTERED }

@Serializable
data class Account(
    val userId: UserId,
    val deviceId: DeviceId,
    val phoneE164: String,
    val registrationId: Int,
    val registrationState: RegistrationState,
)
