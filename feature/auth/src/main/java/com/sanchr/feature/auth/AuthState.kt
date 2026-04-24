package com.sanchr.feature.auth

/**
 * Sealed hierarchy describing every stage of the Android onboarding flow.
 *
 * Flow (iOS-parity):
 *
 *   Splash -> Home -> (LoginPhone | RegisterPhoneAndName) -> OtpEntry
 *           -> Registering (post-OTP bootstrap pipeline) -> Done
 *
 * [AppLocked] is a parallel authenticated-but-gated landing used when the
 * device has app-lock enabled and a valid session is already on disk.
 *
 * Each state carries exactly the data the next step needs so the view model
 * never has to reach back into [com.sanchr.core.datastore.SessionManager] for
 * in-flight values. [Error] snapshots the prior state so `retry()` can restore
 * it without re-collecting user input.
 */
sealed interface AuthState {
    /** Animated splash shown at cold launch while DI warms up and the session is read. */
    data object Splash : AuthState

    /**
     * Login/Register chooser landing. Mirrors iOS `LoginView`, which surfaces both
     * "I already have an account" and "Create account" choices inline.
     *
     * @param prefilledPhone optional pre-populated subscriber number (e.g. after
     *   returning from the register flow with an already-registered phone).
     */
    data class Home(
        val prefilledPhone: String = "",
    ) : AuthState

    /** Biometric gate shown between Splash and Main for authenticated users with app-lock on. */
    data object AppLocked : AuthState

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
