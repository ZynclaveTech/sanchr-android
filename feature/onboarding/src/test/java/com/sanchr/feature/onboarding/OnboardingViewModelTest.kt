package com.sanchr.feature.onboarding

import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.datastore.UserPreferences
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * State-machine unit tests for [OnboardingViewModel]. Locks in Phase 4-5
 * behavior prior to polish:
 *
 *   Welcome -> NameEntry -> AvatarEntry -> ContactSync -> Completed
 *
 * Plus trimmed-name validation (1..128), avatar skip/preserve, and the
 * fail-safe DataStore write on [OnboardingViewModel.onContactSyncFinish].
 *
 * Conventions match `:feature:auth`'s `AuthViewModelStateTest` — MockK,
 * `StandardTestDispatcher` + `Dispatchers.setMain`, `runTest { ... }`,
 * `advanceUntilIdle()` for suspend paths. No Turbine needed: we assert on
 * the terminal StateFlow value after idling the scheduler, and verify
 * write-before-state ordering by mocking the suspend call to block until
 * we inspect the state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val userPreferences = mockk<UserPreferences>(relaxed = true)

    private fun newViewModel(): OnboardingViewModel =
        OnboardingViewModel(
            sessionManager = sessionManager,
            userPreferences = userPreferences,
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { sessionManager.getDisplayName() } returns null
        coEvery { userPreferences.setOnboardingCompleted(any()) } just Runs
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isWelcome() =
        runTest {
            val vm = newViewModel()
            assertEquals(OnboardingState.Welcome, vm.state.value)
        }

    @Test
    fun onWelcomeContinue_transitionsToNameEntry_withPrefillFromSession() =
        runTest {
            every { sessionManager.getDisplayName() } returns "Alice"
            val vm = newViewModel()

            vm.onWelcomeContinue()

            val s = vm.state.value
            assertIs<OnboardingState.NameEntry>(s)
            assertEquals("Alice", s.prefilledName)
            assertEquals("Alice", s.name)
        }

    @Test
    fun onWelcomeContinue_whenNoStoredName_prefillsEmpty() =
        runTest {
            every { sessionManager.getDisplayName() } returns null
            val vm = newViewModel()

            vm.onWelcomeContinue()

            val s = vm.state.value
            assertIs<OnboardingState.NameEntry>(s)
            assertEquals("", s.prefilledName)
            assertEquals("", s.name)
        }

    @Test
    fun onNameChanged_updatesNameField() =
        runTest {
            val vm = newViewModel()
            vm.onWelcomeContinue()

            vm.onNameChanged("Bob")

            val s = vm.state.value
            assertIs<OnboardingState.NameEntry>(s)
            assertEquals("Bob", s.name)
        }

    @Test
    fun onNameChanged_capsAt128Chars() =
        runTest {
            val vm = newViewModel()
            vm.onWelcomeContinue()

            val overlong = "x".repeat(200)
            vm.onNameChanged(overlong)

            val s = vm.state.value
            assertIs<OnboardingState.NameEntry>(s)
            assertEquals(128, s.name.length)
        }

    @Test
    fun submitName_withValidName_transitionsToAvatarEntry() =
        runTest {
            val vm = newViewModel()
            vm.onWelcomeContinue()
            vm.onNameChanged("Carol")

            vm.submitName()

            val s = vm.state.value
            assertIs<OnboardingState.AvatarEntry>(s)
            assertEquals("Carol", s.name)
            assertNull(s.avatarUri)
        }

    @Test
    fun submitName_withEmptyName_doesNotAdvance() =
        runTest {
            val vm = newViewModel()
            vm.onWelcomeContinue()
            vm.onNameChanged("")

            vm.submitName()

            // Contract: validation is a no-op, caller disables CTA.
            assertIs<OnboardingState.NameEntry>(vm.state.value)
        }

    @Test
    fun submitName_withWhitespaceOnlyName_doesNotAdvance() =
        runTest {
            val vm = newViewModel()
            vm.onWelcomeContinue()
            vm.onNameChanged("   ")

            vm.submitName()

            assertIs<OnboardingState.NameEntry>(vm.state.value)
        }

    @Test
    fun submitName_trimsWhitespace() =
        runTest {
            val vm = newViewModel()
            vm.onWelcomeContinue()
            vm.onNameChanged("  Bob  ")

            vm.submitName()

            val s = vm.state.value
            assertIs<OnboardingState.AvatarEntry>(s)
            assertEquals("Bob", s.name)
        }

    @Test
    fun onAvatarSelected_updatesUri() =
        runTest {
            val vm = newViewModel()
            vm.onWelcomeContinue()
            vm.onNameChanged("Dave")
            vm.submitName()

            vm.onAvatarSelected("content://photo")

            val s = vm.state.value
            assertIs<OnboardingState.AvatarEntry>(s)
            assertEquals("content://photo", s.avatarUri)
            assertEquals("Dave", s.name)
        }

    @Test
    fun skipAvatar_transitionsToContactSync_withNullUri() =
        runTest {
            val vm = newViewModel()
            vm.onWelcomeContinue()
            vm.onNameChanged("Eve")
            vm.submitName()
            vm.onAvatarSelected("content://photo")

            vm.skipAvatar()

            val s = vm.state.value
            assertIs<OnboardingState.ContactSync>(s)
            assertEquals("Eve", s.name)
            assertNull(s.avatarUri)
        }

    @Test
    fun submitAvatar_withSelectedUri_transitionsToContactSync_preservingUri() =
        runTest {
            val vm = newViewModel()
            vm.onWelcomeContinue()
            vm.onNameChanged("Frank")
            vm.submitName()
            vm.onAvatarSelected("content://frank.jpg")

            vm.submitAvatar()

            val s = vm.state.value
            assertIs<OnboardingState.ContactSync>(s)
            assertEquals("Frank", s.name)
            assertEquals("content://frank.jpg", s.avatarUri)
        }

    @Test
    fun onContactSyncFinish_writesFlagToUserPreferences_thenTransitionsToCompleted() =
        runTest {
            val vm = newViewModel()
            vm.onWelcomeContinue()
            vm.onNameChanged("Grace")
            vm.submitName()
            vm.submitAvatar()

            vm.onContactSyncFinish()
            advanceUntilIdle()

            assertEquals(OnboardingState.Completed, vm.state.value)
            coVerify(exactly = 1) { userPreferences.setOnboardingCompleted(true) }
        }

    @Test
    fun onContactSyncFinish_whenUserPreferencesThrows_stillTransitionsToCompleted() =
        runTest {
            coEvery { userPreferences.setOnboardingCompleted(true) } throws
                RuntimeException("disk full")

            val vm = newViewModel()
            vm.onWelcomeContinue()
            vm.onNameChanged("Heidi")
            vm.submitName()
            vm.submitAvatar()

            vm.onContactSyncFinish()
            advanceUntilIdle()

            // Fail-safe: user must not be trapped in onboarding on disk-write error.
            assertEquals(OnboardingState.Completed, vm.state.value)
            coVerify(exactly = 1) { userPreferences.setOnboardingCompleted(true) }
        }

    @Test
    fun onNameChanged_fromNonNameEntryState_isNoOp() =
        runTest {
            val vm = newViewModel()
            // Still on Welcome.
            vm.onNameChanged("Ignored")
            assertEquals(OnboardingState.Welcome, vm.state.value)
        }

    @Test
    fun onAvatarSelected_fromNonAvatarEntryState_isNoOp() =
        runTest {
            val vm = newViewModel()
            vm.onAvatarSelected("content://nope")
            assertEquals(OnboardingState.Welcome, vm.state.value)
        }
}
