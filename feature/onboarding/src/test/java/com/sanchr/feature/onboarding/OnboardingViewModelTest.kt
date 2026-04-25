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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * State-machine unit tests for [OnboardingViewModel].
 *
 * Phase H5a re-aligned the flow to iOS: there is no longer a pre-name
 * `Welcome` landing. The state machine is now:
 *
 *   NameEntry -> AvatarEntry -> ContactSync -> WelcomeConfirm -> Completed
 *
 * Tests cover:
 *  - Initial state seeded from SessionManager display name (placeholder only;
 *    bound input always starts empty for iOS parity).
 *  - Trimmed-name validation (1..128).
 *  - Avatar skip / preserve.
 *  - ContactSync -> WelcomeConfirm transition (no DataStore write here).
 *  - finishOnboarding flips isSubmitting, writes the DataStore flag, and
 *    emits Completed.
 *  - finishOnboarding debounces double-taps via the isSubmitting guard.
 *  - back() during an in-flight submit is a no-op.
 *  - Fail-safe: DataStore throw still emits Completed.
 *  - notificationGranted updates WelcomeConfirm.notificationsEnabled.
 *  - back() from WelcomeConfirm returns to ContactSync preserving state.
 *  - back() from ContactSync returns to AvatarEntry preserving state.
 *  - back() from ContactSync no-ops while ContactSync.isSubmitting is true.
 *  - back() from NameEntry is a no-op.
 *
 * Conventions match `:feature:auth`'s `AuthViewModelStateTest` — MockK,
 * `StandardTestDispatcher` + `Dispatchers.setMain`, `runTest { ... }`,
 * `advanceUntilIdle()` for suspend paths.
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
    fun initialState_isNameEntry_withEmptyPrefill_whenNoStoredName() =
        runTest {
            every { sessionManager.getDisplayName() } returns null
            val vm = newViewModel()

            val s = vm.state.value
            assertIs<OnboardingState.NameEntry>(s)
            assertEquals("", s.prefilledName)
            assertEquals("", s.name)
        }

    @Test
    fun initialState_isNameEntry_withStoredDisplayName_placeholderOnly_inputEmpty() =
        runTest {
            every { sessionManager.getDisplayName() } returns "Alice"
            val vm = newViewModel()

            val s = vm.state.value
            assertIs<OnboardingState.NameEntry>(s)
            // iOS parity: prefilledName drives placeholder text; the bound
            // input always starts empty so the user's first keystroke does
            // not have to clear pre-existing text.
            assertEquals("Alice", s.prefilledName)
            assertEquals("", s.name)
        }

    @Test
    fun onNameChanged_updatesNameField() =
        runTest {
            val vm = newViewModel()

            vm.onNameChanged("Bob")

            val s = vm.state.value
            assertIs<OnboardingState.NameEntry>(s)
            assertEquals("Bob", s.name)
        }

    @Test
    fun onNameChanged_capsAt128Chars() =
        runTest {
            val vm = newViewModel()

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
            vm.onNameChanged("")

            vm.submitName()

            assertIs<OnboardingState.NameEntry>(vm.state.value)
        }

    @Test
    fun submitName_withWhitespaceOnlyName_doesNotAdvance() =
        runTest {
            val vm = newViewModel()
            vm.onNameChanged("   ")

            vm.submitName()

            assertIs<OnboardingState.NameEntry>(vm.state.value)
        }

    @Test
    fun submitName_trimsWhitespace() =
        runTest {
            val vm = newViewModel()
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
    fun onContactSyncFinish_transitionsToWelcomeConfirm_withoutWritingDataStore() =
        runTest {
            val vm = newViewModel()
            vm.onNameChanged("Grace")
            vm.submitName()
            vm.onAvatarSelected("content://grace.jpg")
            vm.submitAvatar()

            vm.onContactSyncFinish()
            advanceUntilIdle()

            val s = vm.state.value
            assertIs<OnboardingState.WelcomeConfirm>(s)
            assertEquals("Grace", s.name)
            assertEquals("content://grace.jpg", s.avatarUri)
            assertFalse(s.notificationsEnabled)
            assertNull(s.errorMessage)
            assertFalse(s.isSubmitting)
            // DataStore write deferred to finishOnboarding.
            coVerify(exactly = 0) { userPreferences.setOnboardingCompleted(any()) }
        }

    @Test
    fun finishOnboarding_writesDataStoreFlag_thenEmitsCompleted() =
        runTest {
            val vm = newViewModel()
            vm.onNameChanged("Heidi")
            vm.submitName()
            vm.submitAvatar()
            vm.onContactSyncFinish()
            advanceUntilIdle()

            vm.finishOnboarding()
            advanceUntilIdle()

            assertEquals(OnboardingState.Completed, vm.state.value)
            coVerify(exactly = 1) { userPreferences.setOnboardingCompleted(true) }
        }

    @Test
    fun finishOnboarding_setsIsSubmittingTrue_whileWriteIsInFlight() =
        runTest {
            // Suspend the DataStore write until we explicitly release it so we
            // can observe the mid-flight WelcomeConfirm(isSubmitting = true).
            val gate = CompletableDeferred<Unit>()
            coEvery { userPreferences.setOnboardingCompleted(true) } coAnswers
                {
                    gate.await()
                }

            val vm = newViewModel()
            vm.onNameChanged("Heidi")
            vm.submitName()
            vm.submitAvatar()
            vm.onContactSyncFinish()
            advanceUntilIdle()

            vm.finishOnboarding()
            // Drive the launched coroutine up to its first suspension point
            // (the gated DataStore write).
            advanceUntilIdle()

            val midFlight = vm.state.value
            assertIs<OnboardingState.WelcomeConfirm>(midFlight)
            assertTrue(midFlight.isSubmitting)

            // Release the gate; the coroutine should now emit Completed.
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(OnboardingState.Completed, vm.state.value)
            coVerify(exactly = 1) { userPreferences.setOnboardingCompleted(true) }
        }

    @Test
    fun finishOnboarding_doubleTap_isDebouncedByIsSubmittingGuard() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            coEvery { userPreferences.setOnboardingCompleted(true) } coAnswers
                {
                    gate.await()
                }

            val vm = newViewModel()
            vm.onNameChanged("Heidi")
            vm.submitName()
            vm.submitAvatar()
            vm.onContactSyncFinish()
            advanceUntilIdle()

            vm.finishOnboarding()
            advanceUntilIdle()
            // Second tap while the first write is gated must be a no-op
            // (isSubmitting guard) — no second DataStore write should be
            // launched.
            vm.finishOnboarding()
            advanceUntilIdle()

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(OnboardingState.Completed, vm.state.value)
            coVerify(exactly = 1) { userPreferences.setOnboardingCompleted(true) }
        }

    @Test
    fun back_duringInFlightSubmit_isNoOp() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            coEvery { userPreferences.setOnboardingCompleted(true) } coAnswers
                {
                    gate.await()
                }

            val vm = newViewModel()
            vm.onNameChanged("Heidi")
            vm.submitName()
            vm.onAvatarSelected("content://heidi.jpg")
            vm.submitAvatar()
            vm.onContactSyncFinish()
            advanceUntilIdle()

            vm.finishOnboarding()
            advanceUntilIdle()

            // back() must be ignored mid-submit; otherwise the launched
            // coroutine would race and overwrite the user's ContactSync
            // state with Completed.
            vm.back()
            val midFlight = vm.state.value
            assertIs<OnboardingState.WelcomeConfirm>(midFlight)
            assertTrue(midFlight.isSubmitting)
            assertEquals("Heidi", midFlight.name)
            assertEquals("content://heidi.jpg", midFlight.avatarUri)

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(OnboardingState.Completed, vm.state.value)
        }

    @Test
    fun finishOnboarding_whenUserPreferencesThrows_stillEmitsCompleted() =
        runTest {
            coEvery { userPreferences.setOnboardingCompleted(true) } throws
                RuntimeException("disk full")

            val vm = newViewModel()
            vm.onNameChanged("Ivan")
            vm.submitName()
            vm.submitAvatar()
            vm.onContactSyncFinish()
            advanceUntilIdle()

            vm.finishOnboarding()
            advanceUntilIdle()

            // Fail-safe: user must not be trapped in onboarding on disk-write error.
            assertEquals(OnboardingState.Completed, vm.state.value)
            coVerify(exactly = 1) { userPreferences.setOnboardingCompleted(true) }
        }

    @Test
    fun notificationGranted_updatesNotificationsEnabledOnWelcomeConfirm() =
        runTest {
            val vm = newViewModel()
            vm.onNameChanged("Judy")
            vm.submitName()
            vm.submitAvatar()
            vm.onContactSyncFinish()

            vm.notificationGranted(true)

            val s = vm.state.value
            assertIs<OnboardingState.WelcomeConfirm>(s)
            assertTrue(s.notificationsEnabled)
        }

    @Test
    fun notificationGranted_whenDenied_setsFlagFalse() =
        runTest {
            val vm = newViewModel()
            vm.onNameChanged("Kim")
            vm.submitName()
            vm.submitAvatar()
            vm.onContactSyncFinish()
            vm.notificationGranted(true)

            vm.notificationGranted(false)

            val s = vm.state.value
            assertIs<OnboardingState.WelcomeConfirm>(s)
            assertFalse(s.notificationsEnabled)
        }

    @Test
    fun notificationGranted_fromNonWelcomeConfirmState_isNoOp() =
        runTest {
            val vm = newViewModel()
            // Still in NameEntry.
            vm.notificationGranted(true)

            assertIs<OnboardingState.NameEntry>(vm.state.value)
        }

    @Test
    fun back_fromWelcomeConfirm_returnsToContactSync_preservingNameAndAvatar() =
        runTest {
            val vm = newViewModel()
            vm.onNameChanged("Laura")
            vm.submitName()
            vm.onAvatarSelected("content://laura.jpg")
            vm.submitAvatar()
            vm.onContactSyncFinish()

            vm.back()

            val s = vm.state.value
            assertIs<OnboardingState.ContactSync>(s)
            assertEquals("Laura", s.name)
            assertEquals("content://laura.jpg", s.avatarUri)
        }

    @Test
    fun back_fromContactSync_returnsToAvatarEntry_preservingNameAndAvatar() =
        runTest {
            val vm = newViewModel()
            vm.onNameChanged("Alice")
            vm.submitName()
            vm.onAvatarSelected("content://photo")
            vm.submitAvatar()
            // Now in ContactSync(name = "Alice", avatarUri = "content://photo").
            assertIs<OnboardingState.ContactSync>(vm.state.value)

            vm.back()

            val s = vm.state.value
            assertIs<OnboardingState.AvatarEntry>(s)
            assertEquals("Alice", s.name)
            assertEquals("content://photo", s.avatarUri)
            assertFalse(s.isSubmitting)
        }

    @Test
    fun back_fromContactSync_whileSubmitting_isNoOp() =
        runTest {
            // ContactSync.isSubmitting has no public setter today (the future
            // contact-discovery RPC will own it). To pin the VM's documented
            // `if (current.isSubmitting) return` guard for that future flow,
            // we seed the private MutableStateFlow via reflection. Java
            // reflection drives a non-suppressed setValue(Object) call on the
            // MutableStateFlow, sidestepping the unchecked-cast warning that
            // a Kotlin-level cast would produce.
            val vm = newViewModel()
            vm.onNameChanged("Alice")
            vm.submitName()
            vm.onAvatarSelected("content://photo")
            vm.submitAvatar()
            assertIs<OnboardingState.ContactSync>(vm.state.value)

            val submittingState =
                OnboardingState.ContactSync(
                    name = "Alice",
                    avatarUri = "content://photo",
                    isSubmitting = true,
                )
            val stateField =
                OnboardingViewModel::class.java.getDeclaredField("_state").apply {
                    isAccessible = true
                }
            val flow = stateField.get(vm)
            val setValue =
                flow.javaClass.methods
                    .first {
                        it.name == "setValue" && it.parameterTypes.size == 1
                    }.apply { isAccessible = true }
            setValue.invoke(flow, submittingState)
            assertEquals(submittingState, vm.state.value)

            vm.back()

            assertEquals(submittingState, vm.state.value)
        }

    @Test
    fun back_fromNameEntry_isNoOp() =
        runTest {
            val vm = newViewModel()
            // Still in NameEntry.
            vm.back()

            assertIs<OnboardingState.NameEntry>(vm.state.value)
        }

    @Test
    fun onNameChanged_fromNonNameEntryState_isNoOp() =
        runTest {
            val vm = newViewModel()
            vm.onNameChanged("Mallory")
            vm.submitName()
            // Now in AvatarEntry.

            vm.onNameChanged("Ignored")

            val s = vm.state.value
            assertIs<OnboardingState.AvatarEntry>(s)
            assertEquals("Mallory", s.name)
        }

    @Test
    fun onAvatarSelected_fromNonAvatarEntryState_isNoOp() =
        runTest {
            val vm = newViewModel()
            // Still in NameEntry.
            vm.onAvatarSelected("content://nope")

            assertIs<OnboardingState.NameEntry>(vm.state.value)
        }
}
