package com.sanchr.domain.messaging

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.common.Result
import com.sanchr.core.crypto.DeviceEncryptedMessage
import com.sanchr.core.crypto.SignalSessionManager
import com.sanchr.core.database.entity.MessageEntity
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.model.MessageStatus
import com.sanchr.proto.messaging.MessagingServiceClient
import com.sanchr.proto.messaging.SealedDeviceMessage
import com.sanchr.proto.messaging.SendSealedMessageRequest
import com.sanchr.proto.messaging.SendSealedMessageResponse
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.signal.libsignal.protocol.UntrustedIdentityException

class SendMessageUseCaseTest {
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val sessionManager = mockk<SessionManager>()
    private val signalSessionManager = mockk<SignalSessionManager>()
    private val messagingClient = mockk<MessagingServiceClient>()
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
        SendMessageUseCase(
            messageRepository,
            sessionManager,
            signalSessionManager,
            messagingClient,
            deliveryTokenStore,
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
        // Read by encryptFanOut when it builds the InnerPayload, before the
        // per-recipient encryptForAllDevices calls — so every test that
        // reaches the fan-out needs this stubbed, not just the happy path.
        every { sessionManager.getDeviceId() } returns "7"
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

    @Test
    fun `attemptSend marks FAILED with UNTRUSTED_IDENTITY when peer identity rotates`() =
        runTest {
            primeCommonMocks()
            coEvery {
                signalSessionManager.encryptForAllDevices(any(), "peer-uuid")
            } throws UntrustedIdentityException("peer-uuid", mockk(relaxed = true))

            val result = useCase.attemptSend(entity)

            assertTrue(result is Result.Error)
            assertTrue(result.exception is UntrustedIdentityException)
            coVerify(exactly = 1) {
                messageRepository.markSendFailed(
                    messageId = "msg-1",
                    failureReason = "Peer's safety number changed",
                    failureClass = FailureClass.UNTRUSTED_IDENTITY,
                )
            }
            coVerify(exactly = 0) {
                messageRepository.requeueAfterFailure(any())
            }
            coVerify(exactly = 0) {
                messagingClient.sendMessage(any())
            }
        }

    @Test
    fun `attemptSend sends a sealed InnerPayload over SendSealedMessage`() =
        runTest {
            primeCommonMocks()
            val deliveryToken = byteArrayOf(9, 8, 7)
            coEvery { deliveryTokenStore.acquire() } returns deliveryToken

            val plaintextSlot = slot<ByteArray>()
            coEvery {
                signalSessionManager.encryptForAllDevices(capture(plaintextSlot), "peer-uuid")
            } returns
                listOf(
                    DeviceEncryptedMessage(deviceId = 1, ciphertext = byteArrayOf(1, 2, 3), messageType = 3, registrationId = 42),
                    DeviceEncryptedMessage(deviceId = 2, ciphertext = byteArrayOf(4, 5, 6), messageType = 3, registrationId = 42),
                )

            val requestSlot = slot<SendSealedMessageRequest>()
            coEvery {
                messagingClient.sendSealedMessage(capture(requestSlot))
            } returns SendSealedMessageResponse(serverTimestamp = 5_000L)

            val result = useCase.attemptSend(entity)

            assertTrue(result is Result.Success)

            coVerify(exactly = 0) { messagingClient.sendMessage(any()) }
            coVerify(exactly = 1) { messagingClient.sendSealedMessage(any()) }
            coVerify(exactly = 1) { deliveryTokenStore.acquire() }
            coVerify(exactly = 1) { deliveryTokenStore.replenishIfNeeded() }
            coVerify(exactly = 1) {
                messageRepository.adoptServerId(
                    oldMessageId = "msg-1",
                    newMessageId = "msg-1",
                    serverTimestamp = 5_000L,
                )
            }
            // Pins the sequence, not just the call counts above — a
            // regression that moved replenishIfNeeded() before acquire()
            // (spending it on a stale token count) would still pass a
            // call-count-only check but fails this.
            coVerifyOrder {
                deliveryTokenStore.acquire()
                messagingClient.sendSealedMessage(any())
                deliveryTokenStore.replenishIfNeeded()
            }

            val request = requestSlot.captured
            assertTrue(request.deliveryToken.contentEquals(deliveryToken))
            assertEquals(
                listOf(
                    SealedDeviceMessage(
                        recipientId = "peer-uuid",
                        deviceId = 1,
                        sealedEnvelope = byteArrayOf(1, 2, 3),
                        conversationId = "conv-1",
                        silent = false,
                    ),
                    SealedDeviceMessage(
                        recipientId = "peer-uuid",
                        deviceId = 2,
                        sealedEnvelope = byteArrayOf(4, 5, 6),
                        conversationId = "conv-1",
                        silent = false,
                    ),
                ),
                request.deviceMessages,
            )

            val payload = InnerPayload.decode(plaintextSlot.captured)
            assertTrue(payload != null)
            assertEquals("hello", String(payload!!.content, Charsets.UTF_8))
            assertEquals("text", payload.contentType)
            assertEquals("msg-1", payload.messageId)
            assertEquals("conv-1", payload.conversationId)
            assertEquals("self-uuid", payload.senderUserId)
            assertEquals(7, payload.senderDeviceId)
        }

    @Test
    fun `attemptSend fails without acquiring a token when fan-out exceeds 100 device messages`() =
        runTest {
            primeCommonMocks()
            val oversizedFanOut =
                (1..101).map { deviceId ->
                    DeviceEncryptedMessage(
                        deviceId = deviceId,
                        ciphertext = byteArrayOf(deviceId.toByte()),
                        messageType = 3,
                        registrationId = 42,
                    )
                }
            coEvery {
                signalSessionManager.encryptForAllDevices(any(), "peer-uuid")
            } returns oversizedFanOut

            val result = useCase.attemptSend(entity)

            assertTrue(result is Result.Error)
            assertTrue(result.exception is TooManyDeviceMessagesException)
            coVerify(exactly = 1) {
                messageRepository.markSendFailed(
                    messageId = "msg-1",
                    failureReason = any(),
                    failureClass = FailureClass.TOO_MANY_RECIPIENTS,
                )
            }
            coVerify(exactly = 0) { deliveryTokenStore.acquire() }
            coVerify(exactly = 0) { messagingClient.sendSealedMessage(any()) }
            coVerify(exactly = 0) { messagingClient.sendMessage(any()) }
            coVerify(exactly = 0) { messageRepository.requeueAfterFailure(any()) }
        }

    @Test
    fun `attemptSend still reports SENT when adoptServerId throws after a successful sealed send`() =
        runTest {
            primeCommonMocks()
            coEvery { deliveryTokenStore.acquire() } returns byteArrayOf(9, 8, 7)
            coEvery {
                signalSessionManager.encryptForAllDevices(any(), "peer-uuid")
            } returns
                listOf(
                    DeviceEncryptedMessage(deviceId = 1, ciphertext = byteArrayOf(1, 2, 3), messageType = 3, registrationId = 42),
                )
            coEvery {
                messagingClient.sendSealedMessage(any())
            } returns SendSealedMessageResponse(serverTimestamp = 5_000L)
            // The RPC has already succeeded by the time this local write is
            // attempted — a full disk, a disk I/O error, or cancellation on
            // process death are all plausible causes on a device already
            // flagged as disk-constrained.
            coEvery {
                messageRepository.adoptServerId(any(), any(), any())
            } throws RuntimeException("disk full")

            val result = useCase.attemptSend(entity)

            // The send genuinely succeeded server-side; a local bookkeeping
            // failure on adoptServerId must not turn that into a reported
            // failure, and must not requeue or FAILED a message the server
            // already has.
            assertTrue(result is Result.Success)
            assertEquals(MessageStatus.SENT, (result as Result.Success).data.status)
            assertEquals("msg-1", result.data.id)
            coVerify(exactly = 0) { messageRepository.requeueAfterFailure(any()) }
            coVerify(exactly = 0) { messageRepository.markSendFailed(any(), any(), any()) }
            // The token top-up is independent bookkeeping too, and must
            // still run even though adoptServerId failed.
            coVerify(exactly = 1) { deliveryTokenStore.replenishIfNeeded() }
        }

    @Test
    fun `attemptSend does not swallow cancellation from adoptServerId as a reported SENT`() =
        runTest {
            primeCommonMocks()
            coEvery { deliveryTokenStore.acquire() } returns byteArrayOf(9, 8, 7)
            coEvery {
                signalSessionManager.encryptForAllDevices(any(), "peer-uuid")
            } returns
                listOf(
                    DeviceEncryptedMessage(deviceId = 1, ciphertext = byteArrayOf(1, 2, 3), messageType = 3, registrationId = 42),
                )
            coEvery {
                messagingClient.sendSealedMessage(any())
            } returns SendSealedMessageResponse(serverTimestamp = 5_000L)
            coEvery {
                messageRepository.adoptServerId(any(), any(), any())
            } throws CancellationException("scope cancelled")

            val result = useCase.attemptSend(entity)

            // attemptSendOrThrow's own try/catch must rethrow the
            // CancellationException rather than logging-and-continuing the
            // way it does for an ordinary write failure (the sibling test
            // above) — absorbing it here would let the function return a
            // normal Message(status = SENT) after its scope was already
            // cancelled, a structured-concurrency violation. The exception
            // still surfaces as a Result.Error rather than as a genuine
            // thrown cancellation, because runCatchingResult (core:common)
            // wraps every use-case call in its own broad `catch (e:
            // Exception)`; that shared wrapper predates this task and is out
            // of scope here. What this test pins is the property this task
            // owns: the exception that reaches that wrapper is still the
            // real CancellationException — not swallowed and replaced by a
            // fabricated success.
            assertTrue(result is Result.Error)
            assertTrue(result.exception is CancellationException)
            coVerify(exactly = 0) { messageRepository.requeueAfterFailure(any()) }
            coVerify(exactly = 0) { messageRepository.markSendFailed(any(), any(), any()) }
        }
}
