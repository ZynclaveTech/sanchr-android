package com.sanchr.core.network.link

import java.io.IOException
import java.net.Proxy
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okio.Buffer
import okio.ForwardingSource
import okio.buffer

/** A link out of a message is read up to a limit and through the guarded client. */
class GuardedLinkDownloaderTest {
    @Test
    fun `a body within the limit is returned whole`() {
        val body = ByteArray(100) { it.toByte() }
        assertContentEquals(body, GuardedLinkDownloader.readCapped(Buffer().write(body), maxBytes = 100))
        assertContentEquals(ByteArray(0), GuardedLinkDownloader.readCapped(Buffer(), maxBytes = 100))
    }

    @Test
    fun `a body over the limit is refused`() {
        assertFailsWith<IOException> { GuardedLinkDownloader.readCapped(Buffer().write(ByteArray(101)), maxBytes = 100) }
    }

    @Test
    fun `an endless body is abandoned just past the limit, not read to the end`() {
        var served = 0L
        val endless =
            object : ForwardingSource(Buffer()) {
                override fun read(
                    sink: Buffer,
                    byteCount: Long,
                ): Long {
                    val chunk = minOf(byteCount, 8_192L)
                    sink.write(ByteArray(chunk.toInt()))
                    served += chunk
                    return chunk
                }
            }

        assertFailsWith<IOException> { GuardedLinkDownloader.readCapped(endless.buffer(), maxBytes = 100_000) }

        assertTrue(served < 200_000, "read $served bytes of an endless body")
    }

    @Test
    fun `the client it is built with is the guarded one`() {
        val client = with(LinkPreviewGuard) { okhttp3.OkHttpClient.Builder().refusePrivateAddresses() }.build()
        assertEquals(Proxy.NO_PROXY, client.proxy)
        assertFalse(client.followRedirects)
        assertTrue(client.dns is LinkPreviewGuard.PublicOnlyDns)
    }
}
