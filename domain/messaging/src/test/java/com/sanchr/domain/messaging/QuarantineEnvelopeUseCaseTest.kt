package com.sanchr.domain.messaging

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.database.dao.QuarantinedEnvelopeDao
import com.sanchr.core.database.entity.QuarantinedEnvelopeEntity
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.Test
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest

class QuarantineEnvelopeUseCaseTest {
    private val dao = mockk<QuarantinedEnvelopeDao>(relaxed = true)
    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
            override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
            override val signalDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        }
    private val useCase = QuarantineEnvelopeUseCase(dao, dispatchers)

    @Test
    fun quarantine_inserts_entity_with_expected_fields() =
        runTest {
            val payload = byteArrayOf(1, 2, 3)
            useCase.quarantine(
                envelopeId = "env-1",
                payload = payload,
                receivedAt = 1234L,
                senderUserId = "alice",
                senderDeviceId = 2,
                failureClass = FailureClass.INVALID_MESSAGE,
                failureMessage = "bad mac",
            )

            coVerify {
                dao.insert(
                    QuarantinedEnvelopeEntity(
                        envelopeId = "env-1",
                        receivedAt = 1234L,
                        payload = payload,
                        senderUserId = "alice",
                        senderDeviceId = 2,
                        failureClass = "INVALID_MESSAGE",
                        failureMessage = "bad mac",
                        attempts = 0,
                    ),
                )
            }
        }

    @Test
    fun quarantine_handles_nullable_sender_fields() =
        runTest {
            useCase.quarantine(
                envelopeId = "env-2",
                payload = ByteArray(0),
                receivedAt = 99L,
                senderUserId = null,
                senderDeviceId = null,
                failureClass = FailureClass.CRYPTO_OTHER,
                failureMessage = null,
            )

            coVerify {
                dao.insert(
                    QuarantinedEnvelopeEntity(
                        envelopeId = "env-2",
                        receivedAt = 99L,
                        payload = ByteArray(0),
                        senderUserId = null,
                        senderDeviceId = null,
                        failureClass = "CRYPTO_OTHER",
                        failureMessage = null,
                        attempts = 0,
                    ),
                )
            }
        }
}
