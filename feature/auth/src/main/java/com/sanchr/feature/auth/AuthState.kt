package com.sanchr.feature.auth

/**
 * Sealed hierarchy describing every stage of the Android onboarding flow:
 *
 *  PhoneEntry -> ProfileEntry -> OtpEntry -> Permissions -> Registering -> Done
 *
 * Each state carries exactly the data the next step needs so the view model
 * never has to reach back into [com.sanchr.core.datastore.SessionManager] for
 * in-flight values. [Error] snapshots the prior state so `retry()` can restore
 * it without re-collecting user input.
 */
sealed interface AuthState {
    data class PhoneEntry(
        val countryCode: String = "+1",
        val phone: String = "",
        val isSubmitting: Boolean = false,
    ) : AuthState

    data class ProfileEntry(
        val phoneE164: String,
        val displayName: String = "",
        val isSubmitting: Boolean = false,
    ) : AuthState

    data class OtpEntry(
        val phoneE164: String,
        val displayName: String,
        val otp: String = "",
        val isSubmitting: Boolean = false,
    ) : AuthState

    data class Permissions(
        val phoneE164: String,
        val displayName: String,
        val userId: String,
        val deviceId: Int,
    ) : AuthState

    data class Registering(
        val step: RegistrationStep,
        val context: Permissions,
    ) : AuthState

    data object Done : AuthState

    data class Error(
        val previousState: AuthState,
        val message: String,
    ) : AuthState
}

/**
 * Ordered stages of the post-OTP bootstrap pipeline. The pipeline runs
 * strictly in declaration order; each stage is idempotent so retry from
 * [AuthState.Error] simply re-runs the whole pipeline rather than checkpointing.
 */
enum class RegistrationStep {
    GENERATING_KEYS,
    UPLOADING_KEYS,
    FETCHING_SENDER_CERT,
    REGISTERING_PUSH,
    PERSISTING,
}
