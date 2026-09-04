package com.sanchr.domain.messaging

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.DeviceEncryptedMessage
import com.sanchr.core.crypto.SignalSessionManager
import com.sanchr.core.crypto.profile.ProfileKeyStore
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.datastore.UserPreferences
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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import sanchr.messaging.Messaging

class SendReadReceiptUseCaseTest {
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val sessionManager = mockk<SessionManager>()
    private val userPreferences = mockk<UserPreferences>()
    private val signalSessionManager = mockk<SignalSessionManager>()
    private val messagingClient = mockk<MessagingServiceClient>()
    private val deliveryTokenStore = mockk<DeliveryTokenStore>()
    private val receiptClock = mockk<ReceiptClock>()
    private val receiptJitter = mockk<ReceiptJitter>()
    private val ownProfileKey = ByteArray(32) { 0x5A }
    private val profileKeyStore = mockk<ProfileKeyStore> { every { ownProfileKey() } returns ownProfileKey }
    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
            override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
            override val signalDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        }

    // A real SendMessageUseCase so encryptAndWrapDeviceMessages (the shared
    // fan-out loop) runs for real; only its own leaf dependency
    // (signalSessionManager) is mocked. This mirrors production wiring,
    // where SendReadReceiptUseCase reuses this same instance.
    private val sendMessageUseCase =
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

    private val useCase =
        SendReadReceiptUseCase(
            messageRepository,
            sessionManager,
            userPreferences,
            sendMessageUseCase,
            deliveryTokenStore,
            messagingClient,
            dispatchers,
            receiptClock,
            receiptJitter,
            profileKeyStore,
        )

    private fun primeCommonMocks() {
        every { sessionManager.getUserId() } returns "self-uuid"
        every { userPreferences.readReceiptsEnabled } returns flowOf(true)
        coEvery { messageRepository.oneToOneRecipient("conv-1", "self-uuid") } returns "peer-uuid"
        coEvery { receiptJitter.await() } just Runs
        every { receiptClock.nowEpochMillis() } returns 42_000L
        coEvery { deliveryTokenStore.acquire() } returns byteArrayOf(9, 8, 7)
        coEvery { messagingClient.sendSealedMessage(any()) } returns SendSealedMessageResponse(serverTimestamp = 5_000L)
    }

    @Test
    fun `invoke does nothing when read receipts are disabled`() =
        runTest {
            every { sessionManager.getUserId() } returns "self-uuid"
            every { userPreferences.readReceiptsEnabled } returns flowOf(false)

            useCase.invoke("conv-1", "msg-1")

            coVerify(exactly = 0) { messageRepository.oneToOneRecipient(any(), any()) }
            coVerify(exactly = 0) { receiptJitter.await() }
            coVerify(exactly = 0) { deliveryTokenStore.acquire() }
            coVerify(exactly = 0) { messagingClient.sendSealedMessage(any()) }
        }

    @Test
    fun `invoke sends nothing for a group conversation`() =
        runTest {
            every { sessionManager.getUserId() } returns "self-uuid"
            every { userPreferences.readReceiptsEnabled } returns flowOf(true)
            coEvery { messageRepository.oneToOneRecipient("conv-1", "self-uuid") } returns null

            useCase.invoke("conv-1", "msg-1")

            coVerify(exactly = 0) { receiptJitter.await() }
            coVerify(exactly = 0) { deliveryTokenStore.acquire() }
            coVerify(exactly = 0) { messagingClient.sendSealedMessage(any()) }
        }

    @Test
    fun `invoke sends one sealed silent message per peer device for a 1-to-1 conversation`() =
        runTest {
            primeCommonMocks()
            val plaintextSlot = slot<ByteArray>()
            coEvery {
                signalSessionManager.encryptForAllDevices(capture(plaintextSlot), "peer-uuid")
            } returns
                listOf(
                    DeviceEncryptedMessage(deviceId = 1, ciphertext = byteArrayOf(1, 2, 3), messageType = 3, registrationId = 42),
                    DeviceEncryptedMessage(deviceId = 2, ciphertext = byteArrayOf(4, 5, 6), messageType = 3, registrationId = 42),
                )
            val requestSlot = slot<SendSealedMessageRequest>()
            coEvery { messagingClient.sendSealedMessage(capture(requestSlot)) } returns SendSealedMessageResponse(serverTimestamp = 5_000L)

            useCase.invoke("conv-1", "msg-1")

            coVerify(exactly = 1) { receiptJitter.await() }
            coVerify(exactly = 1) { deliveryTokenStore.acquire() }
            coVerify(exactly = 1) { messagingClient.sendSealedMessage(any()) }

            val request = requestSlot.captured
            assertEquals(
                listOf(
                    SealedDeviceMessage(
                        recipientId = "peer-uuid",
                        deviceId = 1,
                        sealedEnvelope = byteArrayOf(1, 2, 3),
                        conversationId = "",
                        silent = true,
                    ),
                    SealedDeviceMessage(
                        recipientId = "peer-uuid",
                        deviceId = 2,
                        sealedEnvelope = byteArrayOf(4, 5, 6),
                        conversationId = "",
                        silent = true,
                    ),
                ),
                request.deviceMessages,
            )
        }

    @Test
    fun `never acquires a token before the preference and peer are resolved`() =
        runTest {
            primeCommonMocks()
            coEvery {
                signalSessionManager.encryptForAllDevices(any(), "peer-uuid")
            } returns listOf(DeviceEncryptedMessage(deviceId = 1, ciphertext = byteArrayOf(1), messageType = 3, registrationId = 42))

            useCase.invoke("conv-1", "msg-1")

            coVerifyOrder {
                userPreferences.readReceiptsEnabled
                messageRepository.oneToOneRecipient("conv-1", "self-uuid")
                deliveryTokenStore.acquire()
                messagingClient.sendSealedMessage(any())
            }
        }

    @Test
    fun `the envelope payload carries an empty conversation id and null message id`() =
        runTest {
            primeCommonMocks()
            val plaintextSlot = slot<ByteArray>()
            coEvery {
                signalSessionManager.encryptForAllDevices(capture(plaintextSlot), "peer-uuid")
            } returns listOf(DeviceEncryptedMessage(deviceId = 1, ciphertext = byteArrayOf(1), messageType = 3, registrationId = 42))

            useCase.invoke("conv-1", "msg-1")

            val payload = InnerPayload.decode(plaintextSlot.captured)
            assertTrue(payload != null)
            assertEquals("", payload!!.conversationId)
            assertEquals(null, payload.messageId)
            assertEquals("receipt/v1", payload.contentType)

            val receipt = Messaging.ReceiptUpdate.parseFrom(payload.content)
            assertEquals("conv-1", receipt.conversationId)
            assertEquals("msg-1", receipt.messageId)
            assertEquals("self-uuid", receipt.recipientId)
            assertEquals("read", receipt.status)
            assertEquals(42_000L, receipt.timestamp)
        }

    @Test
    fun `a send failure is logged and dropped, not thrown`() =
        runTest {
            primeCommonMocks()
            coEvery {
                signalSessionManager.encryptForAllDevices(any(), "peer-uuid")
            } returns listOf(DeviceEncryptedMessage(deviceId = 1, ciphertext = byteArrayOf(1), messageType = 3, registrationId = 42))
            coEvery { messagingClient.sendSealedMessage(any()) } throws RuntimeException("network down")

            // Must not throw.
            useCase.invoke("conv-1", "msg-1")
        }

    @Test
    fun `cancellation from sendSealedMessage escapes invoke rather than being swallowed`() =
        runTest {
            // This exact class of bug — a broad catch absorbing
            // CancellationException instead of rethrowing it — has been
            // fixed three times on this project (phase 0's SessionRefresher,
            // 1b-1's adoptServerIdSafely/replenishTokenPoolSafely, and the
            // catch order this use case copies). Pinned directly rather than
            // trusted by convention.
            primeCommonMocks()
            coEvery {
                signalSessionManager.encryptForAllDevices(any(), "peer-uuid")
            } returns listOf(DeviceEncryptedMessage(deviceId = 1, ciphertext = byteArrayOf(1), messageType = 3, registrationId = 42))
            coEvery { messagingClient.sendSealedMessage(any()) } throws CancellationException("scope cancelled")

            assertFailsWith<CancellationException> {
                useCase.invoke("conv-1", "msg-1")
            }
        }

    @Test
    fun `sends nothing when the peer has no key-capable devices`() =
        runTest {
            primeCommonMocks()
            coEvery {
                signalSessionManager.encryptForAllDevices(any(), "peer-uuid")
            } returns emptyList()

            useCase.invoke("conv-1", "msg-1")

            coVerify(exactly = 0) { deliveryTokenStore.acquire() }
            coVerify(exactly = 0) { messagingClient.sendSealedMessage(any()) }
        }
}
