package com.sanchr.feature.onboarding

/**
 * State machine for the post-auth onboarding flow.
 *
 * iOS reference: `OnboardingViewModel.swift` (currentStep 1..3 mapped to Name ->
 * Avatar -> ContactSync). Android adds an explicit [Welcome] entry step
 * (mirroring `OnboardingWelcomeStepView`) and a terminal [Completed] signal so
 * the NavHost gating in Phase 5 can distinguish "still onboarding" from
 * "finished, go to Main".
 *
 * Step ordering (matches iOS exactly, plus Welcome landing):
 *   Welcome -> NameEntry -> AvatarEntry -> ContactSync -> Completed
 *
 * Error wraps the previous state so the UI can re-render the same screen with
 * an inline error banner, identical to `AuthState.Error` in `:feature:auth`.
 */
sealed interface OnboardingState {
    data object Welcome : OnboardingState

    data class NameEntry(
        val prefilledName: String = "",
        val name: String = prefilledName,
        val isSubmitting: Boolean = false,
    ) : OnboardingState

    data class AvatarEntry(
        val name: String,
        val avatarUri: String? = null,
        val isSubmitting: Boolean = false,
    ) : OnboardingState

    data class ContactSync(
        val name: String,
        val avatarUri: String?,
        val isSubmitting: Boolean = false,
    ) : OnboardingState

    data object Completed : OnboardingState

    data class Error(
        val previous: OnboardingState,
        val message: String,
    ) : OnboardingState
}
