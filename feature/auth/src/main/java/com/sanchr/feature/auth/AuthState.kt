package com.sanchr.feature.auth

/**
 * Sealed hierarchy describing every stage of the Android onboarding flow.
 *
 * Flow (iOS-parity):
 *
 *   Splash -> LoginPhone -> OtpEntry -> Registering -> Done
 *          \-> RegisterPhoneAndName -> OtpEntry ...
 *
 * The unauthenticated path lands directly on [LoginPhone], matching the iOS
 * canonical `Splash -> LoginView` transition in `SanchrApp.swift:350-396`.
 * [LoginPhoneScreen] surfaces a "New to Sanchr? Sign up" footer affordance
 * that switches to [RegisterPhoneAndName]; Android keeps the dual-path UI
 * while the backend still exposes split Login/Register entry points.
 *
 * Each state carries exactly the data the next step needs so the view model
 * never has to reach back into [com.sanchr.core.datastore.SessionManager] for
 * in-flight values. [Error] snapshots the prior state so `retry()` can restore
 * it without re-collecting user input.
 */
sealed interface AuthState {
    /** Animated splash shown at cold launch while DI warms up and the session is read. */
    data object Splash : AuthState

    /** Phone-only existing-user entry. Mirrors iOS `LoginView` phone screen. */
    data class LoginPhone(
        val countryCode: String = "+1",
        val phone: String = "",
        val isSubmitting: Boolean = false,
    ) : AuthState

    /** Combined phone+displayName new-user entry. Mirrors iOS `RegisterView`. */
    data class RegisterPhoneAndName(
        val countryCode: String = "+1",
        val phone: String = "",
        val displayName: String = "",
        val isSubmitting: Boolean = false,
    ) : AuthState

    /** OTP-verification step shared by both login and register paths. */
    data class OtpEntry(
        val phoneE164: String,
        val displayName: String,
        val otp: String = "",
        val isSubmitting: Boolean = false,
    ) : AuthState

    /**
     * Post-OTP bootstrap pipeline in flight. Carries the verified session
     * context so a failure-retry can resume without re-verifying the OTP.
     */
    data class Registering(
        val step: RegistrationStep,
        val phoneE164: String,
        val displayName: String,
        val userId: String,
        val deviceId: Int,
    ) : AuthState

    /** Terminal success state; the NavHost observer fires `onAuthSuccess`. */
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
