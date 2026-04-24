package com.sanchr.app.bootstrap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
         * Live authentication state. `SanchrNavHost` collects this to react to
         * logout — when it flips `false` while the user is inside the main
         * graph, the nav host pops back to the auth graph without requiring a
         * process restart.
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
         *  2. A non-blank [SessionManager.getDisplayName] — covers the
         *     returning-user-on-a-new-device path: the server already has a
         *     display name on file, so there is no useful onboarding to
         *     re-run. Without this fallback, a re-install or device swap would
         *     force an existing user back through onboarding, which iOS
         *     explicitly avoids.
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
         */
        val hasCompletedOnboarding: StateFlow<Boolean> =
            combine(
                userPreferences.hasCompletedOnboardingFlow,
                sessionManager.isAuthenticated,
            ) { flagValue, _ ->
                flagValue || !sessionManager.getDisplayName().isNullOrBlank()
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
    }
