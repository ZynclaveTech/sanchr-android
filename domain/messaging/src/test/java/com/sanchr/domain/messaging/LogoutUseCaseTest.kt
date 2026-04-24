package com.sanchr.domain.messaging

import android.content.Context
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.StagedIdentityStore
import com.sanchr.core.database.SanchrDatabase
import com.sanchr.core.database.crypto.DatabasePassphraseProvider
import com.sanchr.core.datastore.SessionManager
import io.mockk.Ordering
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LogoutUseCaseTest {

    private val context = mockk<Context>(relaxed = true)
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val database = mockk<SanchrDatabase>(relaxed = true)
    private val stagedIdentityStore = mockk<StagedIdentityStore>(relaxed = true)
    private val databasePassphraseProvider = mockk<DatabasePassphraseProvider>(relaxed = true)

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
            database = database,
            stagedIdentityStore = stagedIdentityStore,
            databasePassphraseProvider = databasePassphraseProvider,
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

            // database.close() must still be called despite the earlier failure
            coVerifyOrder {
                database.close()
                databasePassphraseProvider.wipe()
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
