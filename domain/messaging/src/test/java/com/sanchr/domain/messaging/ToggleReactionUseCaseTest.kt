package com.sanchr.domain.messaging

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.model.MessageReaction
import com.sanchr.proto.messaging.MessagingServiceClient
import com.sanchr.proto.messaging.Reaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ToggleReactionUseCaseTest {
    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
            override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
            override val signalDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        }
    private val repo = mockk<MessageRepository>(relaxed = true)
    private val client = mockk<MessagingServiceClient>()
    private val session = mockk<SessionManager> { every { getUserId() } returns "self" }
    private val useCase = ToggleReactionUseCase(repo, client, session, dispatchers)

    @Test
    fun `a first reaction is applied locally and sent as an add`() =
        runTest {
            coEvery { repo.reactionsFor("m1") } returns emptyList()
            coEvery { client.sendReaction(any()) } answers { firstArg() }

            assertEquals(true, useCase("c1", "m1", "❤️"))

            coVerify { repo.applyReaction("m1", "self", "❤️", removed = false, timestampMillis = any()) }
            coVerify {
                client.sendReaction(
                    match {
                        it.messageId == "m1" &&
                            it.conversationId == "c1" &&
                            it.userId == "self" &&
                            it.emoji == "❤️" &&
                            !it.removed &&
                            it.timestamp > 0
                    },
                )
            }
        }

    @Test
    fun `reacting again with the same emoji removes it`() =
        runTest {
            coEvery { repo.reactionsFor("m1") } returns listOf(MessageReaction("❤️", "self", Instant.fromEpochMilliseconds(1)))
            coEvery { client.sendReaction(any()) } answers { firstArg<Reaction>() }

            assertEquals(false, useCase("c1", "m1", "❤️"))

            coVerify { repo.applyReaction("m1", "self", "❤️", removed = true, timestampMillis = any()) }
            coVerify { client.sendReaction(match { it.removed }) }
        }

    @Test
    fun `a server failure reverts the local change and surfaces the error`() =
        runTest {
            coEvery { repo.reactionsFor("m1") } returns emptyList()
            coEvery { client.sendReaction(any()) } throws IOException("offline")

            assertThrows(IOException::class.java) { kotlinx.coroutines.runBlocking { useCase("c1", "m1", "👍") } }

            coVerifyOrder {
                repo.applyReaction("m1", "self", "👍", removed = false, timestampMillis = any())
                repo.applyReaction("m1", "self", "👍", removed = true, timestampMillis = any())
            }
        }
}
