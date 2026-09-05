package com.sanchr.core.network.media

import kotlin.test.Test
import kotlin.test.assertEquals
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer

class ProgressRequestBodyTest {
    @Test
    fun `counts bytes actually written and ends at the full length`() {
        val payload = ByteArray(9_000) { 1 }
        val seen = mutableListOf<Pair<Long, Long>>()
        val body =
            ProgressRequestBody(payload.toRequestBody("application/octet-stream".toMediaType())) { sent, total ->
                seen +=
                    sent to total
            }

        val sink = Buffer()
        body.writeTo(sink)

        assertEquals(payload.size.toLong(), sink.size)
        assertEquals(payload.size.toLong(), seen.last().first)
        assertEquals(payload.size.toLong(), body.contentLength())
        assertEquals(listOf(payload.size.toLong()), seen.map { it.second }.distinct())
        assertEquals(seen.map { it.first }.sorted(), seen.map { it.first })
    }
}
