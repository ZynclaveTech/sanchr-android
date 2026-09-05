package com.sanchr.app.data

import com.sanchr.proto.calling.CallLogEntry
import com.sanchr.proto.calling.CallSignalingServiceClient
import com.sanchr.proto.calling.GetCallHistoryRequest
import com.sanchr.proto.calling.GetCallHistoryResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallRepositoryImplTest {
    private val client = mockk<CallSignalingServiceClient>()

    @Test
    fun `history maps direction, status and duration, and never carries the server's peer name`() =
        runTest {
            coEvery { client.getCallHistory(GetCallHistoryRequest(limit = 20)) } returns
                GetCallHistoryResponse(
                    listOf(
                        CallLogEntry("c1", "alice", "Sanchr User", "video", "outgoing", "completed", 100, 190, 90),
                        CallLogEntry("c2", "bob", "Bob", "voice", "incoming", "missed", 200, 200, 0),
                    ),
                )

            val records = CallRepositoryImpl(client).observeCallHistory(limit = 20).first()

            assertEquals(listOf("c1", "c2"), records.map { it.id })
            assertTrue(records[0].isOutgoing)
            assertTrue(records[0].isVideo)
            assertEquals(90L, records[0].durationSeconds)
            assertFalse(records[0].isMissed)
            assertFalse(records[1].isOutgoing)
            assertTrue(records[1].isMissed)
            assertTrue(records.all { it.remoteUserName.isEmpty() })
            assertEquals("alice", records[0].remoteUserId)
        }
}
