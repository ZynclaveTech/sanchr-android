package com.sanchr.feature.onboarding

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.profile.EncryptedProfileUpdater
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.datastore.UserPreferences
import com.sanchr.core.mediaviewer.ImageSendEncoder
import com.sanchr.core.network.media.AvatarUploader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 *  - [back] supports the chevron.left back button on both WelcomeConfirm
 *    (-> ContactSync) and AvatarEntry (-> NameEntry, Phase H6 iOS-parity
 *    `OnboardingAvatarStepView` chevron). Preserves carried-forward fields
 *    (name, avatarUri). No-ops while a submit is in flight to prevent racing
 *    the [finishOnboarding] coroutine on WelcomeConfirm.
 *
 * The profile is written at the end of the avatar step, as iOS writes it in
 * `OnboardingAvatarStepView` -> `saveProfile`. It used to be written nowhere:
 * `submitAvatar` only advanced the state machine, and the claim that the name
 * had "already been saved at OTP time" does not hold for a new account — the
 * OTP step stores whatever the server returned, which for a new account is
 * blank. So a new user typed a name, picked a photo, and finished onboarding
 * with neither on the server nor on the device.
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
        private val profileUpdater: EncryptedProfileUpdater,
        private val avatarUploader: AvatarUploader,
        @ApplicationContext private val appContext: Context,
        private val dispatchers: DispatcherProvider,
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

        /**
         * "Skip for now" on the Avatar step. Still saves the profile: the name
         * was typed on the previous step and skipping a photo is not skipping
         * the name.
         */
        fun skipAvatar() = saveProfileAndAdvance(includeAvatar = false)

        /** Advance Avatar -> ContactSync, saving name and photo first. */
        fun submitAvatar() = saveProfileAndAdvance(includeAvatar = true)

        /**
         * Uploads the chosen photo (when there is one), writes the profile
         * under our own Profile Key, stores the plaintext on the device, and
         * only then advances.
         *
         * The write has to succeed before moving on: the name is the whole
         * point of the two preceding steps, and advancing past a failure is
         * what left accounts with no name at all. A failure is reported on the
         * step so it can be retried.
         */
        private fun saveProfileAndAdvance(includeAvatar: Boolean) {
            val current = _state.value as? OnboardingState.AvatarEntry ?: return
            if (current.isSubmitting) return
            _state.value = current.copy(isSubmitting = true, errorMessage = null)
            viewModelScope.launch {
                try {
                    val avatarUri = current.avatarUri.takeIf { includeAvatar }
                    val avatarUrl = avatarUri?.let { uploadAvatar(it) }.orEmpty()
                    profileUpdater.update(displayName = current.name, bio = "", avatarUrl = avatarUrl)
                    // Our own profile is not readable back from the server —
                    // `GetSettings` carries no profile fields — so the device
                    // has to keep what it just set.
                    sessionManager.saveProfileDetails(displayName = current.name, bio = "", avatarUrl = avatarUrl)
                    _state.value =
                        OnboardingState.ContactSync(
                            name = current.name,
                            avatarUri = avatarUri,
                        )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save the onboarding profile", e)
                    _state.value =
                        current.copy(
                            isSubmitting = false,
                            errorMessage = "Couldn't save your profile. Check your connection and try again.",
                        )
                }
            }
        }

        private suspend fun uploadAvatar(uri: String): String {
            val picked =
                withContext(dispatchers.io) {
                    // Re-encoded rather than uploaded as picked: the original
                    // carries EXIF, and a camera photo's EXIF carries where it
                    // was taken. An avatar is the most widely shared image an
                    // account has, so it is the last one to publish a location.
                    ImageSendEncoder.encode(
                        appContext.contentResolver,
                        uri.toUri(),
                        maxLongEdge = AVATAR_MAX_EDGE,
                    )
                } ?: error("could not read the selected photo")
            return avatarUploader.upload(picked.bytes, picked.mimeType)
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
         * Back-nav supporting three transitions:
         *   - [OnboardingState.WelcomeConfirm] -> [OnboardingState.ContactSync]
         *     (mirrors `OnboardingWelcomeStepView` toolbar `chevron.left`).
         *   - [OnboardingState.ContactSync]    -> [OnboardingState.AvatarEntry]
         *     (Phase H7 iOS parity — `ContactSyncView` leading `arrow.left`).
         *   - [OnboardingState.AvatarEntry]    -> [OnboardingState.NameEntry]
         *     (Phase H6 iOS parity — `OnboardingAvatarStepView` chevron-back).
         *
         * Preserves carried-forward fields. WelcomeConfirm no-ops if a submit
         * is in flight, to prevent racing an in-flight [finishOnboarding]
         * coroutine that would otherwise overwrite the ContactSync state with
         * Completed after the user pressed back. ContactSync no-ops while
         * `isSubmitting` is true (the screen owns the permission launcher
         * round-trip) so back can't fire mid-launch. AvatarEntry has no
         * in-flight submit gate today (server upload is deferred to a later
         * phase), so back is unconditional from that step.
         *
         * For NameEntry we re-fetch the cached display name as the
         * placeholder, matching `initialState()` (the user may have typed and
         * advanced; we restore an empty bound input + the cached prefill,
         * which is what iOS does because the iOS step is index-based and the
         * `TextField` re-reads `prefilledName` from the VM on each render).
         */
        fun back() {
            when (val current = _state.value) {
                is OnboardingState.WelcomeConfirm -> {
                    if (current.isSubmitting) return
                    _state.value =
                        OnboardingState.ContactSync(
                            name = current.name,
                            avatarUri = current.avatarUri,
                        )
                }
                is OnboardingState.ContactSync -> {
                    if (current.isSubmitting) return
                    _state.value =
                        OnboardingState.AvatarEntry(
                            name = current.name,
                            avatarUri = current.avatarUri,
                            isSubmitting = false,
                        )
                }
                is OnboardingState.AvatarEntry -> {
                    val prefill = sessionManager.getDisplayName().orEmpty()
                    _state.value =
                        OnboardingState.NameEntry(
                            prefilledName = prefill,
                            name = current.name,
                        )
                }
                else -> Unit
            }
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
            /** Avatars never render above 96dp, so 1024 is already generous. */
            private const val AVATAR_MAX_EDGE = 1024
            private const val MAX_NAME_LENGTH = 128
            private const val TAG = "OnboardingViewModel"
        }
    }
