package com.sanchr.domain.messaging

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.common.Result
import com.sanchr.proto.auth.AuthServiceClient
import com.sanchr.proto.auth.DeleteAccountRequest
import com.sanchr.proto.auth.DeleteAccountResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * The ordering in [DeleteAccountUseCase] is the whole point of the class:
 * the server call authenticates with the Bearer token that the local wipe
 * destroys, so wiping first would strand a live account on a credential-less
 * device. These tests pin that ordering and the failure behaviour.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeleteAccountUseCaseTest {
    private val testDispatcher = StandardTestDispatcher()

    private val authServiceClient = mockk<AuthServiceClient>()
    private val logoutUseCase = mockk<LogoutUseCase>()
    private val dispatchers =
        mockk<DispatcherProvider>().also {
            every { it.io } returns testDispatcher
        }

    private fun useCase() = DeleteAccountUseCase(authServiceClient, logoutUseCase, dispatchers)

    @Test
    fun `deletes on the server before wiping locally`() =
        runTest(testDispatcher) {
            coEvery { authServiceClient.deleteAccount(any()) } returns DeleteAccountResponse(success = true)
            coEvery { logoutUseCase() } returns kotlin.Result.success(Unit)

            val result = useCase()()

            assertIs<Result.Success<Unit>>(result)
            coVerifyOrder {
                authServiceClient.deleteAccount(any<DeleteAccountRequest>())
                logoutUseCase()
            }
        }

    @Test
    fun `a failed server delete leaves local state intact`() =
        runTest(testDispatcher) {
            coEvery { authServiceClient.deleteAccount(any()) } throws IllegalStateException("UNAVAILABLE")
            coEvery { logoutUseCase() } returns kotlin.Result.success(Unit)

            val result = useCase()()

            assertIs<Result.Error>(result)
            coVerify(exactly = 0) { logoutUseCase() }
        }
}
