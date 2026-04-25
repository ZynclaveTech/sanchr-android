package com.sanchr.feature.onboarding

/**
 * State machine for the post-auth onboarding flow.
 *
 * iOS reference: `OnboardingViewModel.swift` (currentStep 1..3 mapped to Name ->
 * Avatar -> ContactSync) and `OnboardingWelcomeStepView.swift` (the post-profile
 * "YOU'RE ALL SET" terminal confirmation).
 *
 * Phase H5a re-aligned the Android state machine to iOS: Welcome is no longer
 * a pre-name landing screen; it is the post-ContactSync terminal confirmation
 * step. Step ordering now matches iOS exactly:
 *
 *   NameEntry -> AvatarEntry -> ContactSync -> WelcomeConfirm -> Completed
 *
 * [Completed] is retained as the post-`finishOnboarding` terminal that the
 * NavHost gating flow observes to route out of `:feature:onboarding`.
 *
 * [Error] wraps the previous state so the UI can re-render the same screen
 * with an inline error banner, identical to `AuthState.Error` in
 * `:feature:auth`.
 */
sealed interface OnboardingState {
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

    /**
     * Terminal confirmation step ("YOU'RE ALL SET" on iOS). Reached after
     * ContactSync; advancing past this state runs `finishOnboarding()`, which
     * persists the DataStore flag and then emits [Completed].
     *
     * iOS reference: `OnboardingWelcomeStepView.swift:15-130` plus
     * `OnboardingViewModel.swift` `notificationsEnabled` field.
     */
    data class WelcomeConfirm(
        val name: String,
        val avatarUri: String?,
        val notificationsEnabled: Boolean = false,
        val errorMessage: String? = null,
        val isSubmitting: Boolean = false,
    ) : OnboardingState

    data object Completed : OnboardingState

    data class Error(
        val previous: OnboardingState,
        val message: String,
    ) : OnboardingState
}
