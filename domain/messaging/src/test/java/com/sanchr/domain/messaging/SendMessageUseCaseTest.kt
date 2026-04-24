package com.sanchr.domain.messaging

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.common.Result
import com.sanchr.core.crypto.SignalSessionManager
import com.sanchr.core.database.entity.MessageEntity
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.model.MessageStatus
import com.sanchr.proto.messaging.MessagingServiceClient
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest

class SendMessageUseCaseTest {
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val sessionManager = mockk<SessionManager>()
    private val signalSessionManager = mockk<SignalSessionManager>()
    private val messagingClient = mockk<MessagingServiceClient>()
    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
            override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
            override val signalDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        }

    private val useCase =
        SendMessageUseCase(
            messageRepository,
            sessionManager,
            signalSessionManager,
            messagingClient,
            dispatchers,
        )

    private val entity =
        MessageEntity(
            id = "msg-1",
            conversationId = "conv-1",
            senderId = "self-uuid",
            contentType = "text",
            contentBody = "hello",
            status = MessageStatus.QUEUED.name,
            timestamp = 1_000L,
        )

    private fun primeCommonMocks() {
        every { sessionManager.getUserId() } returns "self-uuid"
        coEvery {
            messageRepository.recordSendAttempt(any(), any())
        } returns 1
        coEvery {
            messageRepository.getOutboundRecipients(any(), any())
        } returns listOf("peer-uuid")
        coEvery {
            messageRepository.markSendFailed(any(), any(), any())
        } just Runs
    }

    @Test
    fun `attemptSend marks row FAILED with NO_RECIPIENTS when peer has no devices`() =
        runTest {
            primeCommonMocks()
            // Peer exists in the conversation but has zero key-capable
            // devices — SignalSessionManager swallows this and returns an
            // empty list (see its contract), so the fan-out is empty and
            // the guard must mark the row FAILED rather than shipping an
            // empty-device SendMessage RPC (which the server treats as a
            // successful no-op — the "silent SENT" bug).
            coEvery {
                signalSessionManager.encryptForAllDevices(any(), "peer-uuid")
            } returns emptyList()

            val result = useCase.attemptSend(entity)

            assertTrue(result is Result.Error)
            assertTrue(result.exception is NoRecipientsReachableException)
            coVerify(exactly = 1) {
                messageRepository.markSendFailed(
                    messageId = "msg-1",
                    failureReason = "No recipients reachable",
                    failureClass = FailureClass.NO_RECIPIENTS,
                )
            }
            coVerify(exactly = 0) {
                messagingClient.sendMessage(any())
            }
            coVerify(exactly = 0) {
                messageRepository.requeueAfterFailure(any())
            }
        }
}
