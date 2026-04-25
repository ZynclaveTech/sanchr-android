package com.sanchr.feature.onboarding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.datastore.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the onboarding flow.
 *
 * iOS reference: `OnboardingViewModel.swift:60-112` (`saveProfile` +
 * `goToNextStep`/`goToPreviousStep` + `requestNotificationPermission`).
 * Android splits the combined `currentStep: Int` + mutable fields into the
 * [OnboardingState] sealed hierarchy because Compose state-hoisting is
 * idiomatically per-step.
 *
 * Phase H5a (iOS-parity terminal Welcome):
 *  - Initial state is now [OnboardingState.NameEntry] (matches iOS step 1).
 *    The pre-name `Welcome` landing screen has been removed.
 *  - [onContactSyncFinish] now advances to [OnboardingState.WelcomeConfirm]
 *    instead of [OnboardingState.Completed]. The DataStore write is deferred
 *    to [finishOnboarding].
 *  - [finishOnboarding] (CTA on the WelcomeConfirm screen) flips
 *    `isSubmitting = true` synchronously to debounce double-taps and race
 *    with `back()`, writes `UserPreferences.setOnboardingCompleted(true)`,
 *    and then emits [OnboardingState.Completed]. Fail-safe contract
 *    preserved: a DataStore write failure is logged and swallowed; we still
 *    emit Completed so the user is not trapped in onboarding.
 *  - [notificationGranted] is the only VM-side notification surface: the
 *    screen owns the OS permission launcher (Compose
 *    `rememberLauncherForActivityResult`) and reports the resolved flag back
 *    via this setter. On API < 33, the screen should call this with `true`
 *    directly because POST_NOTIFICATIONS is implicitly granted pre-Tiramisu.
 *  - [back] supports the chevron.left back button on WelcomeConfirm
 *    (`OnboardingWelcomeStepView` toolbar leading item) by returning to
 *    ContactSync while preserving name + avatarUri. No-ops while a submit
 *    is in flight to prevent racing the [finishOnboarding] coroutine.
 *
 * Avatar persistence to `ProfileService.UpdateProfile` is still deferred
 * (Phase H5b/H6 — see `OnboardingViewModel.swift:86-97`). Display name was
 * already saved at OTP time in Phase H2.
 *
 * Validation: display name is trimmed, length 1..128. Matches iOS
 * `isNameValid` (>=2 chars) loosely — we accept >=1 and cap at 128 to match
 * the server-side profile name column.
 */
@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val sessionManager: SessionManager,
        private val userPreferences: UserPreferences,
    ) : ViewModel() {
        private val _state: MutableStateFlow<OnboardingState> =
            MutableStateFlow(initialState())
        val state: StateFlow<OnboardingState> = _state.asStateFlow()

        private fun initialState(): OnboardingState {
            val prefill = sessionManager.getDisplayName().orEmpty()
            // iOS parity: prefilledName drives placeholder text only; the
            // bound input starts empty.
            return OnboardingState.NameEntry(prefilledName = prefill, name = "")
        }

        /** TextField onValueChange — 128-char hard cap, no trimming while typing. */
        fun onNameChanged(name: String) {
            val current = _state.value as? OnboardingState.NameEntry ?: return
            _state.value = current.copy(name = name.take(MAX_NAME_LENGTH))
        }

        /**
         * Validates and advances Name -> Avatar. No-op if the trimmed name is
         * outside 1..128. Caller (screen) should disable the CTA accordingly.
         */
        fun submitName() {
            val current = _state.value as? OnboardingState.NameEntry ?: return
            val trimmed = current.name.trim()
            if (trimmed.isEmpty() || trimmed.length > MAX_NAME_LENGTH) return
            _state.value = OnboardingState.AvatarEntry(name = trimmed)
        }

        /** PhotoPicker result — [uri] may be null if user cancelled (treat as no-op). */
        fun onAvatarSelected(uri: String?) {
            val current = _state.value as? OnboardingState.AvatarEntry ?: return
            _state.value = current.copy(avatarUri = uri)
        }

        /** "Skip for now" on Avatar step — advance with no avatar. */
        fun skipAvatar() {
            val current = _state.value as? OnboardingState.AvatarEntry ?: return
            _state.value = OnboardingState.ContactSync(name = current.name, avatarUri = null)
        }

        /**
         * Advance Avatar -> ContactSync. Phase 6 wires
         * `ProfileService.UpdateProfile` + `SessionManager.saveDisplayName`
         * here (see `OnboardingViewModel.swift:86-97`). For now we just move on.
         */
        fun submitAvatar() {
            val current = _state.value as? OnboardingState.AvatarEntry ?: return
            _state.value =
                OnboardingState.ContactSync(
                    name = current.name,
                    avatarUri = current.avatarUri,
                )
        }

        /**
         * ContactSync -> WelcomeConfirm. Phase H5a no longer writes the
         * `has_completed_onboarding` flag here; that write is deferred to
         * [finishOnboarding] when the user taps the CTA on the terminal
         * WelcomeConfirm screen, matching iOS where `saveProfile` runs at the
         * very end of the flow.
         */
        fun onContactSyncFinish() {
            val current = _state.value as? OnboardingState.ContactSync ?: return
            _state.value =
                OnboardingState.WelcomeConfirm(
                    name = current.name,
                    avatarUri = current.avatarUri,
                )
        }

        /**
         * Hook for the screen's `rememberLauncherForActivityResult` to flip
         * the `notificationsEnabled` flag once the OS permission dialog
         * resolves. On API < 33, [OnboardingWelcomeScreen] should call this
         * with `true` directly because POST_NOTIFICATIONS is implicitly
         * granted pre-Tiramisu.
         *
         * iOS reference: `OnboardingViewModel.swift:53-55`
         * (`requestNotificationPermission` -> `notificationsEnabled = granted`).
         */
        fun notificationGranted(granted: Boolean) {
            val current = _state.value as? OnboardingState.WelcomeConfirm ?: return
            _state.value = current.copy(notificationsEnabled = granted)
        }

        /**
         * Back-nav from [OnboardingState.WelcomeConfirm] -> [ContactSync],
         * preserving name + avatarUri. Mirrors iOS
         * `OnboardingWelcomeStepView` toolbar's `chevron.left` leading item.
         *
         * No-ops when [OnboardingState.WelcomeConfirm.isSubmitting] is true,
         * to prevent racing an in-flight [finishOnboarding] coroutine that
         * would otherwise overwrite the ContactSync state with Completed
         * after the user pressed back.
         */
        fun back() {
            val current = _state.value as? OnboardingState.WelcomeConfirm ?: return
            if (current.isSubmitting) return
            _state.value =
                OnboardingState.ContactSync(
                    name = current.name,
                    avatarUri = current.avatarUri,
                )
        }

        /**
         * Terminal CTA on the WelcomeConfirm screen ("Start Chatting" on iOS).
         * Synchronously flips `isSubmitting = true` to debounce double-taps
         * and block [back] from racing the in-flight write, then persists the
         * `has_completed_onboarding` DataStore flag and finally emits
         * [OnboardingState.Completed] so the NavHost routes out to Main. On
         * persistence failure we log and still advance — trapping the user
         * in onboarding over a disk-write error is worse than a one-time
         * re-onboard after relaunch.
         */
        fun finishOnboarding() {
            val current = _state.value as? OnboardingState.WelcomeConfirm ?: return
            if (current.isSubmitting) return
            _state.value = current.copy(isSubmitting = true)
            viewModelScope.launch {
                try {
                    userPreferences.setOnboardingCompleted(true)
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to persist has_completed_onboarding flag", t)
                }
                _state.value = OnboardingState.Completed
            }
        }

        companion object {
            private const val MAX_NAME_LENGTH = 128
            private const val TAG = "OnboardingViewModel"
        }
    }
