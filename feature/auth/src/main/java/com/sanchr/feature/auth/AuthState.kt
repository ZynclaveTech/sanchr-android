package com.sanchr.feature.auth

/**
 * Sealed hierarchy describing every stage of the Android onboarding flow.
 *
 * ## Transitional hierarchy (Phase 1 realignment)
 *
 * This file currently carries **two overlapping sets** of states on purpose:
 *
 *  1. The legacy flow, still live in the running app:
 *
 *      `PhoneEntry -> ProfileEntry -> OtpEntry -> Permissions -> Registering -> Done`
 *
 *  2. The new iOS-parity flow (Splash + split Login/Register + AppLockGate),
 *     added as scaffolding by Phase 1 of `docs/android/auth-onboarding-realignment-plan.md`:
 *
 *      `Splash -> Home -> (LoginPhone | RegisterPhoneAndName) -> OtpEntry -> ... -> Done`
 *      plus `AppLocked` as an authenticated-but-gated state.
 *
 * The new subclasses are **unreferenced by the running navigation graph** in
 * Phase 1 — they exist so Phase 2 (`LoginScreen.kt`/`RegisterScreen.kt` split)
 * and Phase 5 (NavHost re-wiring) can migrate state transitions incrementally
 * without a single god-commit. Once migration is complete, the legacy
 * `PhoneEntry` / `ProfileEntry` / `Permissions` states and the `Done` object
 * will be removed in favour of the new `Home` / `LoginPhone` /
 * `RegisterPhoneAndName` / `Done(isNewUser)` shape described in the plan.
 *
 * Each state carries exactly the data the next step needs so the view model
 * never has to reach back into [com.sanchr.core.datastore.SessionManager] for
 * in-flight values. [Error] snapshots the prior state so `retry()` can restore
 * it without re-collecting user input.
 */
sealed interface AuthState {
    // region ── New iOS-parity states (Phase 1 scaffolding, unreferenced) ────

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

    // endregion

    // region ── Legacy states (still driving the running app) ───────────────

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

    // endregion
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
