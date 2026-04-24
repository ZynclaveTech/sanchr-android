package com.sanchr.feature.onboarding

import androidx.lifecycle.ViewModel
import com.sanchr.core.datastore.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Drives the onboarding flow.
 *
 * iOS reference: `OnboardingViewModel.swift:60-112` (`saveProfile` +
 * `goToNextStep`/`goToPreviousStep`). Android splits the combined
 * `currentStep: Int` + mutable fields into the [OnboardingState] sealed
 * hierarchy because Compose state-hoisting is idiomatically per-step.
 *
 * Phase 4a scope:
 *  - Pure in-memory state transitions. No RPC calls. No `hasCompletedOnboarding`
 *    DataStore write — that flag flip lands in Phase 5 alongside NavHost gating.
 *  - Avatar persistence to `ProfileService.UpdateProfile` lands in Phase 6.
 *    For now the selected URI is held on [OnboardingState.AvatarEntry] /
 *    [OnboardingState.ContactSync] so the UI can preview it.
 *
 * Validation: display name is trimmed, length 1..128. Matches iOS
 * `isNameValid` (>=2 chars) loosely — we accept >=1 and cap at 128 to match
 * the server-side profile name column. Phase 6 will tighten if backend rejects.
 */
@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val sessionManager: SessionManager,
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

        /** Terminal — NavHost observer fires onOnboardingComplete() on this. */
        fun onContactSyncFinish() {
            _state.value = OnboardingState.Completed
        }

        companion object {
            private const val MAX_NAME_LENGTH = 128
        }
    }
