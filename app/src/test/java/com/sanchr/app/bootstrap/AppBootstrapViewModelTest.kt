package com.sanchr.app.bootstrap

import app.cash.turbine.test
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.database.dao.AccountDao
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.datastore.UserPreferences
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Unit tests for [AppBootstrapViewModel]. Locks in the Phase 5 routing
 * decision contract plus the iOS-parity display-name fallback:
 *
 *   hasCompletedOnboarding = DataStore flag OR !getDisplayName().isNullOrBlank()
 *
 * The `combine(...)` is wired so that every emission on
 * [SessionManager.isAuthenticated] retriggers a re-read of
 * [SessionManager.getDisplayName] — that one-shot read is the whole reason
 * isAuthenticated is folded into the combine at all.
 *
 * Patterns match `:feature:auth` / `:feature:onboarding` tests: MockK,
 * `setMain(testDispatcher)`, `runTest { ... advanceUntilIdle() }`.
 * [UnconfinedTestDispatcher] is used so the `Eagerly`-started StateFlow
 * collector picks up upstream values synchronously during construction —
 * matching the production `NavHost` expectation that the value is
 * materialised by the time it first composes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppBootstrapViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val accountDao = mockk<AccountDao>(relaxed = true)
    private val userPreferences = mockk<UserPreferences>(relaxed = true)

    private val sessionActiveFlow = MutableStateFlow(false)
    private val isAuthenticatedFlow = MutableStateFlow(false)
    private val onboardingFlagFlow = MutableStateFlow(false)

    private val dispatchers =
        object : DispatcherProvider {
            override val main = testDispatcher
            override val io = testDispatcher
            override val default = testDispatcher
            override val unconfined = testDispatcher
            override val signalDispatcher = testDispatcher
        }

    private fun newViewModel(): AppBootstrapViewModel =
        AppBootstrapViewModel(
            sessionManager = sessionManager,
            accountDao = accountDao,
            userPreferences = userPreferences,
            dispatchers = dispatchers,
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Default mock wiring — individual tests override as needed.
        every { sessionManager.sessionActive } returns sessionActiveFlow
        every { sessionManager.isAuthenticated } returns isAuthenticatedFlow
        every { sessionManager.getDisplayName() } returns null
        every { sessionManager.getAccessToken() } returns null
        every { accountDao.getCurrentBlocking() } returns null
        every { userPreferences.hasCompletedOnboardingFlow } returns onboardingFlagFlow
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_sessionActive_reflectsSessionManager() =
        runTest(testDispatcher) {
            sessionActiveFlow.value = true
            val vm = newViewModel()
            advanceUntilIdle()
            assertTrue(vm.sessionActive.value)
        }

    @Test
    fun initialState_hasCompletedOnboarding_isFalseWhenFlagFalseAndNoDisplayName() =
        runTest(testDispatcher) {
            onboardingFlagFlow.value = false
            every { sessionManager.getDisplayName() } returns null

            val vm = newViewModel()
            advanceUntilIdle()

            assertFalse(vm.hasCompletedOnboarding.value)
        }

    @Test
    fun hasCompletedOnboarding_trueWhenFlagTrue_regardlessOfDisplayName() =
        runTest(testDispatcher) {
            onboardingFlagFlow.value = true
            every { sessionManager.getDisplayName() } returns null

            val vm = newViewModel()
            advanceUntilIdle()

            assertTrue(vm.hasCompletedOnboarding.value)
        }

    @Test
    fun hasCompletedOnboarding_trueWhenDisplayNamePresent_evenWithFlagFalse() =
        runTest(testDispatcher) {
            onboardingFlagFlow.value = false
            every { sessionManager.getDisplayName() } returns "Alice"

            val vm = newViewModel()
            advanceUntilIdle()

            assertTrue(vm.hasCompletedOnboarding.value)
        }

    @Test
    fun hasCompletedOnboarding_falseWhenDisplayNameIsBlank() =
        runTest(testDispatcher) {
            onboardingFlagFlow.value = false
            every { sessionManager.getDisplayName() } returns "   "

            val vm = newViewModel()
            advanceUntilIdle()

            assertFalse(vm.hasCompletedOnboarding.value)
        }

    @Test
    fun hasCompletedOnboarding_falseWhenDisplayNameIsEmptyString() =
        runTest(testDispatcher) {
            onboardingFlagFlow.value = false
            every { sessionManager.getDisplayName() } returns ""

            val vm = newViewModel()
            advanceUntilIdle()

            assertFalse(vm.hasCompletedOnboarding.value)
        }

    @Test
    fun hasCompletedOnboarding_falseWhenDisplayNameIsSanchrUserPlaceholder() =
        runTest(testDispatcher) {
            // Regression guard for review finding P2 (Phase 8): iOS parity with
            // SanchrApp.swift:340-343 which gates on
            // '!name.isEmpty && name != "Sanchr User"'. The backend returns
            // 'Sanchr User' as a default for accounts that never set a real
            // display name; treating that value as 'onboarded' would let those
            // users silently skip the Name step.
            onboardingFlagFlow.value = false
            every { sessionManager.getDisplayName() } returns "Sanchr User"

            val vm = newViewModel()
            advanceUntilIdle()

            assertFalse(vm.hasCompletedOnboarding.value)
        }

    @Test
    fun hasCompletedOnboarding_reemitsWhenFlagFlipsFalseToTrue() =
        runTest(testDispatcher) {
            onboardingFlagFlow.value = false
            every { sessionManager.getDisplayName() } returns null

            val vm = newViewModel()
            advanceUntilIdle()

            vm.hasCompletedOnboarding.test {
                assertEquals(false, awaitItem())
                onboardingFlagFlow.value = true
                assertEquals(true, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun hasCompletedOnboarding_reemitsWhenAuthStateChanges() =
        runTest(testDispatcher) {
            onboardingFlagFlow.value = false
            // Display name absent at VM init; flips to present by the time
            // the auth-state change triggers the combine to re-read it.
            every { sessionManager.getDisplayName() } returns null

            val vm = newViewModel()
            advanceUntilIdle()
            assertFalse(vm.hasCompletedOnboarding.value)

            every { sessionManager.getDisplayName() } returns "Bob"
            vm.hasCompletedOnboarding.test {
                // Skip current cached value.
                assertEquals(false, awaitItem())
                isAuthenticatedFlow.value = true
                assertEquals(true, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun hasCompletedOnboarding_staysTrueWhenDisplayNameSetByAuthViewModel() =
        runTest(testDispatcher) {
            onboardingFlagFlow.value = false
            every { sessionManager.getDisplayName() } returns null

            val vm = newViewModel()
            advanceUntilIdle()
            assertFalse(vm.hasCompletedOnboarding.value)

            // Simulates AuthViewModel.submitOtp persisting the server-returned
            // display name and flipping authenticated = true.
            every { sessionManager.getDisplayName() } returns "Carol"
            vm.hasCompletedOnboarding.test {
                assertEquals(false, awaitItem())
                isAuthenticatedFlow.value = true
                assertEquals(true, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * DataStore read failures (corrupted preferences file, IO error on disk,
     * migration glitch) must NOT propagate out of the `combine(...).stateIn`
     * collector and crash `viewModelScope`. Instead, the flow is guarded by
     * `.catch { emit(false) }` so onboarding is re-shown — strictly safer
     * than silently skipping it on a corrupted flag.
     *
     * Regression guard for the Phase 7b bug: prior to the fix, the throw
     * propagated out of the `Eagerly`-started stateIn collector and was
     * rethrown by `runTest` as an uncaught exception, failing the test.
     *
     * The flow throws before emitting anything, so `combine` never produces
     * a value from the happy path — `.catch { emit(false) }` is the sole
     * emission path, giving an unambiguous final state of `false`.
     */
    @Test
    fun hasCompletedOnboarding_respectsDataStoreErrors_fallsBackToFalse() =
        runTest(testDispatcher) {
            val failingFlagFlow =
                flow<Boolean> {
                    throw IOException("simulated DataStore read error")
                }
            every { userPreferences.hasCompletedOnboardingFlow } returns failingFlagFlow
            every { sessionManager.getDisplayName() } returns null

            val vm = newViewModel()
            advanceUntilIdle()

            assertFalse(vm.hasCompletedOnboarding.value)
            // Reaching this line at all proves the exception was swallowed
            // by `.catch { emit(false) }` rather than escaping the collector
            // and failing `runTest` with an uncaught `IOException`.
        }
}
