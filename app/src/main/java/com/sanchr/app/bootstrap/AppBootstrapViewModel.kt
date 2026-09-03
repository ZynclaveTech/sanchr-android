package com.sanchr.app.bootstrap

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.app.navigation.PendingDestination
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.database.dao.AccountDao
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.datastore.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Start destination decided at app launch. Driven by a real inspection of
 * persistent state — see [AppBootstrapViewModel].
 */
sealed interface StartDestination {
    data object Loading : StartDestination

    data object Auth : StartDestination

    data object Main : StartDestination
}

/**
 * Decides whether the app should start in the auth flow or the main flow.
 *
 * A session is considered fully registered only if BOTH:
 *  - a persisted access token exists in [SessionManager], and
 *  - a persisted [com.sanchr.core.database.entity.AccountEntity] row exists
 *    with a non-empty identity private key.
 *
 * Half-registered accounts (e.g. tokens present but DB wiped, or account row
 * created but identity key generation failed) deliberately land back at
 * [StartDestination.Auth] so the user re-establishes identity instead of
 * entering the main UI with broken crypto state.
 */
@HiltViewModel
class AppBootstrapViewModel
    @Inject
    constructor(
        private val sessionManager: SessionManager,
        private val accountDao: AccountDao,
        private val userPreferences: UserPreferences,
        private val dispatchers: DispatcherProvider,
    ) : ViewModel() {
        private val _startDestination = MutableStateFlow<StartDestination>(StartDestination.Loading)
        val startDestination: StateFlow<StartDestination> = _startDestination.asStateFlow()

        /**
         * The destination a tapped notification wants to open, held until the
         * session is confirmed active (see `com.sanchr.app.SanchrNavHost`).
         *
         * Lives here rather than as a field on `MainActivity` because a plain
         * Activity field does not survive configuration changes: the manifest
         * declares no `configChanges`, so rotation, a locale change, or the
         * system dark/light setting flipping while the theme preference is
         * System all destroy and recreate the Activity. A `ViewModel` obtained
         * via `by viewModels()` survives that recreation, so a pending tap that
         * arrived just before the flip is not silently forgotten.
         */
        private val _pendingDestination = MutableStateFlow<PendingDestination?>(null)
        val pendingDestination: StateFlow<PendingDestination?> = _pendingDestination.asStateFlow()

        /** Records (or clears, via `null`) the pending notification destination. */
        fun setPendingDestination(destination: PendingDestination?) {
            _pendingDestination.value = destination
        }

        /**
         * Live authentication state. `SanchrNavHost` collects this to react to
         * logout — when it flips `false` while the user is inside the main
         * graph, the nav host pops back to the auth graph without requiring a
         * process restart.
         *
         * Backed by an in-memory `MutableStateFlow` in [SessionManager]; there
         * is no DataStore IO on the collection path, so no `.catch { }` guard
         * is required here.
         */
        val sessionActive: StateFlow<Boolean> = sessionManager.sessionActive

        /**
         * Effective "this user has already onboarded" signal. `SanchrNavHost`
         * observes this in combination with [sessionActive] to route:
         *  - sessionActive && hasCompletedOnboarding → Main
         *  - sessionActive && !hasCompletedOnboarding → Onboarding graph
         *  - !sessionActive → Auth graph
         *
         * Computed as the OR of two signals — matching iOS parity
         * (`SanchrApp.swift:331,340-343`):
         *
         *  1. [UserPreferences.hasCompletedOnboardingFlow] — per-device flag
         *     flipped true when the user finishes the in-app onboarding flow
         *     (Welcome → Name → Avatar → ContactSync).
         *
         *  2. A "real" (non-blank, non-placeholder) [SessionManager.getDisplayName]
         *     — covers the returning-user-on-a-new-device path: the server
         *     already has a display name on file, so there is no useful
         *     onboarding to re-run. Without this fallback, a re-install or
         *     device swap would force an existing user back through
         *     onboarding, which iOS explicitly avoids.
         *
         *     The [PLACEHOLDER_DISPLAY_NAME] guard matches iOS
         *     `SanchrApp.swift:340-343`, which gates on
         *     `!name.isEmpty && name != "Sanchr User"`. The backend returns
         *     `"Sanchr User"` as a default for accounts that never set a real
         *     display name — treating that value as "onboarded" would let
         *     those users skip the Name step and never get prompted to pick
         *     their own name, diverging from iOS behaviour.
         *
         * The display-name check is a one-shot read (EncryptedSharedPreferences
         * has no `Flow` surface), so we piggy-back on
         * [SessionManager.isAuthenticated] as the trigger: every time auth
         * state flips (login / logout), we re-read the display name and
         * recompute. The auth-state value itself is intentionally discarded in
         * the combiner — it is the trigger, not an input.
         *
         * `Eagerly` so the value is materialised by the time `NavHost` first
         * composes — a lazy `WhileSubscribed` would briefly emit the
         * `initialValue = false` default and flash onboarding for already-
         * onboarded users on cold launch.
         *
         * DataStore read failures fall through to a safe-default
         * (not-onboarded) rather than crashing startup: a corrupted or
         * unreadable preferences file would otherwise propagate out of the
         * `stateIn` collector and tear down `viewModelScope`. Falling back to
         * `false` re-shows onboarding — strictly safer than silently skipping
         * it based on a corrupted flag.
         *
         * The safe-default emission happens BEFORE the log call so a
         * mocked/unavailable `android.util.Log` (e.g. in JVM unit tests where
         * `testOptions.unitTests.isReturnDefaultValues` is not set) cannot
         * prevent the fallback from taking effect.
         */
        val hasCompletedOnboarding: StateFlow<Boolean> =
            combine(
                userPreferences.hasCompletedOnboardingFlow,
                sessionManager.isAuthenticated,
            ) { flagValue, _ ->
                val name = sessionManager.getDisplayName()
                val hasRealName = !name.isNullOrBlank() && name != PLACEHOLDER_DISPLAY_NAME
                flagValue || hasRealName
            }.catch { throwable ->
                emit(false)
                runCatching {
                    Log.w(TAG, "hasCompletedOnboarding flow failed; defaulting to false", throwable)
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

        init {
            viewModelScope.launch(dispatchers.io) {
                _startDestination.value = resolve()
            }
        }

        private fun resolve(): StartDestination {
            val hasToken = !sessionManager.getAccessToken().isNullOrEmpty()
            val account = accountDao.getCurrentBlocking()
            val identityKey = account?.identityPrivateKey
            val hasIdentityKey = identityKey != null && identityKey.isNotEmpty()
            return if (hasToken && hasIdentityKey) StartDestination.Main else StartDestination.Auth
        }

        private companion object {
            const val TAG = "AppBootstrap"

            /**
             * Backend default display name for accounts that never set one.
             * Mirrors iOS `SanchrApp.swift:340-343`, which treats this value
             * as "not really onboarded" in its onboarding-skip heuristic.
             */
            const val PLACEHOLDER_DISPLAY_NAME = "Sanchr User"
        }
    }
