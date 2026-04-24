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
 * `goToNextStep`/`goToPreviousStep`). Android splits the combined
 * `currentStep: Int` + mutable fields into the [OnboardingState] sealed
 * hierarchy because Compose state-hoisting is idiomatically per-step.
 *
 * Phase 5 scope additions on top of Phase 4a:
 *  - Terminal [onContactSyncFinish] persists
 *    `UserPreferences.setOnboardingCompleted(true)` BEFORE emitting
 *    [OnboardingState.Completed] so the NavHost's gating flow observes the
 *    flip before routing to Main. Any DataStore write failure is logged and
 *    swallowed — the user must not be trapped in onboarding because of a
 *    disk-write error; Phase X will add a retry on next cold-launch when we
 *    implement the logout wipe path anyway.
 *
 * Still deferred: Avatar persistence to `ProfileService.UpdateProfile` lands
 * in Phase 6 (see `OnboardingViewModel.swift:86-97`).
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
            MutableStateFlow(OnboardingState.Welcome)
        val state: StateFlow<OnboardingState> = _state.asStateFlow()

        /** Welcome -> NameEntry, seeded with any previously-saved display name. */
        fun onWelcomeContinue() {
            val prefill = sessionManager.getDisplayName().orEmpty()
            _state.value = OnboardingState.NameEntry(prefilledName = prefill, name = prefill)
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
         * Terminal step. Persists the `has_completed_onboarding` DataStore flag
         * BEFORE flipping state to [OnboardingState.Completed] so the NavHost
         * sees the updated flag on its next recomposition. On persistence
         * failure we log and still advance — trapping the user in onboarding
         * over a disk-write error is worse than a one-time re-onboard after
         * relaunch.
         */
        fun onContactSyncFinish() {
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
