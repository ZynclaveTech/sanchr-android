package com.sanchr.domain.messaging

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.common.Result
import com.sanchr.core.crypto.DeviceEncryptedMessage
import com.sanchr.core.crypto.SignalSessionManager
import com.sanchr.core.crypto.profile.ProfileKeyStore
import com.sanchr.core.database.entity.MessageEntity
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.datastore.UserPreferences
import com.sanchr.core.model.MessageStatus
import com.sanchr.proto.messaging.MessagingServiceClient
import com.sanchr.proto.messaging.SendSealedMessageRequest
import com.sanchr.proto.messaging.SendSealedMessageResponse
import com.sanchr.proto.messaging.StaleDevice
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.signal.libsignal.protocol.UntrustedIdentityException

/**
 * A contact who reinstalls has lost every session. The server is the one
 * party that can tell, and says so in answer to a send; these cover what the
 * sender then does about it.
 */
class SendMessageUseCaseStaleSessionTest {
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val sessionManager =
        mockk<SessionManager> {
            every { getUserId() } returns "self-uuid"
            every { getDeviceId() } returns "7"
        }
    private val signalSessionManager = mockk<SignalSessionManager>()
    private val messagingClient = mockk<MessagingServiceClient>()
    private val deliveryTokenStore = mockk<DeliveryTokenStore>(relaxed = true) { coEvery { acquire() } returns byteArrayOf(9) }
    private val userPreferences = mockk<UserPreferences> { every { disappearingDefaultSeconds } returns flowOf(0) }
    private val profileKeyStore = mockk<ProfileKeyStore> { every { ownProfileKey() } returns ByteArray(32) }
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
            deliveryTokenStore,
            userPreferences,
            dispatchers,
            profileKeyStore,
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
    private val sent = mutableListOf<SendSealedMessageRequest>()
    private val stale = SendSealedMessageResponse(staleDevices = listOf(StaleDevice("peer-uuid", 1, registered = true)))
    private val accepted = SendSealedMessageResponse(serverTimestamp = 99L)

    private fun prime(
        attempts: Int = 1,
        vararg answers: SendSealedMessageResponse,
    ) {
        coEvery { messageRepository.recordSendAttempt(any(), any()) } returns attempts
        coEvery { messageRepository.getOutboundRecipients(any(), any()) } returns listOf("peer-uuid")
        coEvery { signalSessionManager.resetSession(any(), any()) } just Runs
        // The session the first encryption used was with registration 4242;
        // the one made after the reset is with 9001.
        coEvery { signalSessionManager.encryptForAllDevices(any(), "peer-uuid") } returnsMany
            listOf(encrypted(4242), encrypted(9001))
        coEvery { messagingClient.sendSealedMessage(capture(sent)) } returnsMany answers.toList()
    }

    private fun encrypted(remoteRegistrationId: Int) =
        listOf(
            DeviceEncryptedMessage(
                deviceId = 1,
                ciphertext = byteArrayOf(remoteRegistrationId.toByte()),
                messageType = 0,
                registrationId = 1,
                remoteRegistrationId = remoteRegistrationId,
            ),
        )

    @Test
    fun `a send says which registration it was encrypted for`() =
        runTest {
            prime(1, accepted)

            assertTrue(useCase.attemptSend(entity) is Result.Success)

            assertEquals(
                4242,
                sent
                    .single()
                    .deviceMessages
                    .single()
                    .recipientRegistrationId,
            )
            coVerify(exactly = 0) { signalSessionManager.resetSession(any(), any()) }
        }

    @Test
    fun `told a session is out of date, it starts a new one and sends again`() =
        runTest {
            prime(1, stale, accepted)

            val result = useCase.attemptSend(entity)

            assertTrue(result is Result.Success)
            assertEquals(MessageStatus.SENT, result.data.status)
            assertEquals(listOf(4242, 9001), sent.map { it.deviceMessages.single().recipientRegistrationId })
            coVerifyOrder {
                messagingClient.sendSealedMessage(any())
                signalSessionManager.resetSession("peer-uuid", 1)
                signalSessionManager.encryptForAllDevices(any(), "peer-uuid")
                messagingClient.sendSealedMessage(any())
            }
            // One token each: the server spends it before it looks.
            coVerify(exactly = 2) { deliveryTokenStore.acquire() }
            coVerify(exactly = 0) { messageRepository.requeueAfterFailure(any()) }
            coVerify(exactly = 0) { messageRepository.markSendFailed(any(), any(), any()) }
        }

    @Test
    fun `a refusal is not reported as sent`() =
        runTest {
            prime(1, stale, stale)

            val result = useCase.attemptSend(entity)

            assertTrue(result is Result.Error)
            assertTrue(result.exception is StaleSessionsException)
            coVerify(exactly = 0) { messageRepository.adoptServerId(any(), any(), any()) }
            // Nothing was delivered, so it is tried again later like any
            // other send the server did not take.
            coVerify(exactly = 1) { messageRepository.requeueAfterFailure("msg-1") }
            coVerify(exactly = 2) { messagingClient.sendSealedMessage(any()) }
        }

    @Test
    fun `still refused on the last attempt, the message is failed rather than queued for ever`() =
        runTest {
            prime(3, stale, stale)

            assertTrue(useCase.attemptSend(entity) is Result.Error)

            coVerify(exactly = 1) { messageRepository.markSendFailed("msg-1", any(), FailureClass.CRYPTO_OTHER) }
            coVerify(exactly = 0) { messageRepository.requeueAfterFailure(any()) }
        }

    /**
     * A device that comes back with a different identity key is the
     * safety-number change. The new session must not be made quietly, and the
     * row must end up saying so — not requeued over the top of it.
     */
    @Test
    fun `a contact who came back with a new identity is a safety-number change, not a retry`() =
        runTest {
            prime(1, stale)
            coEvery { signalSessionManager.encryptForAllDevices(any(), "peer-uuid") } returns encrypted(4242) andThenThrows
                UntrustedIdentityException("peer-uuid", mockk(relaxed = true))

            val result = useCase.attemptSend(entity)

            assertTrue(result is Result.Error)
            assertTrue(result.exception is UntrustedIdentityException)
            coVerify(exactly = 1) { messageRepository.markSendFailed("msg-1", any(), FailureClass.UNTRUSTED_IDENTITY) }
            coVerify(exactly = 0) { messageRepository.requeueAfterFailure(any()) }
            coVerify(exactly = 1) { messagingClient.sendSealedMessage(any()) }
        }

    @Test
    fun `a device that is gone altogether leaves nobody to send to`() =
        runTest {
            prime(1, SendSealedMessageResponse(staleDevices = listOf(StaleDevice("peer-uuid", 1, registered = false))))
            coEvery { signalSessionManager.encryptForAllDevices(any(), "peer-uuid") } returns encrypted(4242) andThen emptyList()

            val result = useCase.attemptSend(entity)

            assertTrue(result is Result.Error)
            assertTrue(result.exception is NoRecipientsReachableException)
            coVerify(exactly = 1) { messagingClient.sendSealedMessage(any()) }
        }

    @Test
    fun `a receipt or presence refused the same way is repaired and sent once more`() =
        runTest {
            prime(1, stale, accepted)
            val encrypt = suspend { useCase.encryptAndWrapDeviceMessages(byteArrayOf(1), listOf("peer-uuid"), "", SealedSendKind.Control) }

            useCase.sendControl(encrypt(), encrypt)

            assertEquals(listOf(4242, 9001), sent.map { it.deviceMessages.single().recipientRegistrationId })
            coVerify(exactly = 1) { signalSessionManager.resetSession("peer-uuid", 1) }
        }

    @Test
    fun `a receipt or presence refused twice is left there`() =
        runTest {
            prime(1, stale, stale)
            val encrypt = suspend { useCase.encryptAndWrapDeviceMessages(byteArrayOf(1), listOf("peer-uuid"), "", SealedSendKind.Control) }

            useCase.sendControl(encrypt(), encrypt)

            coVerify(exactly = 2) { messagingClient.sendSealedMessage(any()) }
        }
}
