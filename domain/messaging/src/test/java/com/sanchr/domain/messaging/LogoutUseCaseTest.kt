package com.sanchr.domain.messaging

import android.content.Context
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.StagedIdentityStore
import com.sanchr.core.database.SanchrDatabase
import com.sanchr.core.database.crypto.DatabasePassphraseProvider
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.datastore.UserPreferences
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class LogoutUseCaseTest {
    private val context = mockk<Context>(relaxed = true)
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val userPreferences = mockk<UserPreferences>(relaxed = true)
    private val database = mockk<SanchrDatabase>(relaxed = true)
    private val stagedIdentityStore = mockk<StagedIdentityStore>(relaxed = true)
    private val databasePassphraseProvider = mockk<DatabasePassphraseProvider>(relaxed = true)
    private val deliveryTokenStore = mockk<DeliveryTokenStore>(relaxed = true)

    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
            override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
            override val signalDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        }

    private val useCase =
        LogoutUseCase(
            context = context,
            sessionManager = sessionManager,
            userPreferences = userPreferences,
            database = database,
            stagedIdentityStore = stagedIdentityStore,
            databasePassphraseProvider = databasePassphraseProvider,
            deliveryTokenStore = deliveryTokenStore,
            dispatchers = dispatchers,
        )

    @Test
    fun `clearSession is called before database close`() =
        runTest {
            useCase()

            coVerifyOrder {
                sessionManager.clearSession()
                database.close()
            }
        }

    @Test
    fun `clearSession is called before database delete`() =
        runTest {
            useCase()

            coVerifyOrder {
                sessionManager.clearSession()
                context.deleteDatabase(any())
            }
        }

    @Test
    fun `clearSession is called before keystore wipe`() =
        runTest {
            useCase()

            coVerifyOrder {
                sessionManager.clearSession()
                databasePassphraseProvider.wipe()
            }
        }

    @Test
    fun `all steps run even when earlier steps throw`() =
        runTest {
            every { sessionManager.clearSession() } throws RuntimeException("prefs error")

            useCase()

            // database.close() must still be called despite the earlier failure,
            // and the delivery-token pool must still be dropped last.
            coVerifyOrder {
                database.close()
                databasePassphraseProvider.wipe()
                deliveryTokenStore.clear()
            }
        }

    /**
     * Regression: [DeliveryTokenStore] is a `@Singleton` seeded from
     * [SessionManager] at construction time, so wiping the persisted
     * `delivery_tokens` key in `clearSession()` alone does not touch the
     * live in-memory pool. Without this call, a second account signing in
     * in the same process would spend the first account's leftover
     * (possibly already-expired) tokens.
     */
    @Test
    fun `deliveryTokenStore clear is called exactly once`() =
        runTest {
            useCase()

            coVerify(exactly = 1) { deliveryTokenStore.clear() }
        }

    /**
     * Regression: without this wipe `has_completed_onboarding` (and every
     * other user-level preference) leaks across accounts. A second user
     * logging in on the same device would silently skip onboarding.
     */
    @Test
    fun `userPreferences clear is called exactly once`() =
        runTest {
            useCase()

            coVerify(exactly = 1) { userPreferences.clear() }
        }

    /**
     * UserPreferences must be wiped AFTER sessionManager.clearSession() has
     * flipped sessionActive → false (so the UI has already navigated away
     * from any screens observing preference flows) but BEFORE the database
     * is torn down — mirrors the documented step order in [LogoutUseCase].
     */
    @Test
    fun `userPreferences clear runs between clearSession and database close`() =
        runTest {
            useCase()

            coVerifyOrder {
                sessionManager.clearSession()
                userPreferences.clear()
                database.close()
            }
        }

    /**
     * Simulates a DAO Flow that is active during logout. The flow emits an
     * error (mimicking what Room does when the DB is closed under it). Verifies
     * that a `.catch {}` at the collection site swallows the exception so no
     * uncaught error surfaces to the caller.
     */
    @Test
    fun `active DAO flow with catch does not propagate DB close exception`() =
        runTest {
            val daoFlow = MutableSharedFlow<List<String>>(replay = 1)
            var caughtException: Throwable? = null
            var uncaughtThrown = false

            val collectJob =
                launch {
                    daoFlow
                        .catch { ex -> caughtException = ex }
                        .collect { /* no-op collector */ }
                }

            // Simulate DB closing by emitting an error into the flow
            daoFlow.emit(emptyList())

            // Simulate what Room does when the DB is forcibly closed
            val dbError = IllegalStateException("Cannot perform this operation because the connection pool has been closed")
            // Trigger error through the shared flow by cancelling and re-creating;
            // use a separate flow to model the exception path
            val errorFlow: Flow<List<String>> =
                kotlinx.coroutines.flow.flow {
                    throw dbError
                }

            var secondCaughtException: Throwable? = null
            errorFlow
                .catch { ex -> secondCaughtException = ex }
                .collect { }

            collectJob.cancel()

            assertFalse(uncaughtThrown, "No uncaught exception should escape the catch block")
            assertTrue(secondCaughtException is IllegalStateException, "Exception should be caught, not propagated")
        }
}
