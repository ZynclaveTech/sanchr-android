package com.sanchr.feature.auth

/**
 * Sealed hierarchy describing every stage of the Android onboarding flow.
 *
 * Flow (iOS-parity):
 *
 *   Splash -> LoginPhone -> OtpEntry -> Registering -> Done(isNewUser)
 *
 * The unauthenticated path lands directly on [LoginPhone], matching the iOS
 * canonical `Splash -> LoginView` transition in `SanchrApp.swift:350-396`.
 * [LoginPhone] is the single phone-only entry for both new and returning
 * users; the backend's existing-phone short-circuit
 * (`backend/crates/sanchr-core/src/auth/handlers.rs:244-293`) handles the dispatch
 * transparently. New-user vs returning-user routing is signalled by
 * [Done.isNewUser], computed from the verify-OTP response's `displayName`.
 *
 * Each state carries exactly the data the next step needs so the view model
 * never has to reach back into [com.sanchr.core.datastore.SessionManager] for
 * in-flight values. [Error] snapshots the prior state so `retry()` can restore
 * it without re-collecting user input.
 */
sealed interface AuthState {
    /** Animated splash shown at cold launch while DI warms up and the session is read. */
    data object Splash : AuthState

    /** Phone-only entry. Mirrors iOS `LoginView` phone screen. Single entry for new + returning users. */
    data class LoginPhone(
        val countryCode: String = "+1",
        val phone: String = "",
        val isSubmitting: Boolean = false,
    ) : AuthState

    /** OTP-verification step. */
    data class OtpEntry(
        val phoneE164: String,
        val displayName: String,
        val otp: String = "",
        val isSubmitting: Boolean = false,
        /** The server rejected the code because this number has Registration Lock; the PIN is needed too. */
        val pinRequired: Boolean = false,
        val pin: String = "",
    ) : AuthState {
        /** Verify is allowed once the code is complete and, for a locked number, the PIN is too. */
        val canSubmit: Boolean
            get() = otp.length == OTP_DIGITS && !isSubmitting && (!pinRequired || pin.length == OTP_DIGITS)

        private companion object {
            const val OTP_DIGITS = 6
        }
    }

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

    /**
     * Terminal success state; the NavHost observer fires `onAuthSuccess`.
     *
     * @property isNewUser true when the verify-OTP response carried no
     *   server-side `displayName` (freshly created account). Downstream
     *   onboarding routing uses this to decide whether to send the user
     *   through profile setup. Always false on the fast-login path
     *   (returning user by definition).
     */
    data class Done(
        val isNewUser: Boolean,
    ) : AuthState

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
