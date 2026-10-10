package com.sanchr.core.network.link

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What counts as a link, and so what the preview fetcher is allowed to
 * request.
 *
 * The host used to be taken as everything before the first slash, which
 * includes the userinfo: `https://x.y@127.0.0.1/admin` presented a host of
 * "x.y@127.0.0.1", passed the "contains a dot" check, and the fetcher then
 * made the recipient's phone request its own loopback address on the sender's
 * behalf.
 */
class LinkDetectorTest {
    @Test
    fun `finds the first link, strips trailing punctuation, and upgrades www`() {
        assertEquals("https://example.com/a?b=1", LinkDetector.firstUrl("see https://example.com/a?b=1, then https://other.io"))
        assertEquals("https://www.example.com", LinkDetector.firstUrl("go to www.example.com."))
        assertEquals("http://Example.org/path", LinkDetector.firstUrl("(http://Example.org/path)"))
    }

    @Test
    fun `plain text, bare words and hosts without a dot are not links`() {
        assertNull(LinkDetector.firstUrl("no links here"))
        assertNull(LinkDetector.firstUrl("http://localhost/x"))
        assertNull(LinkDetector.firstUrl("https://"))
    }

    @Test
    fun `userinfo cannot smuggle a private host past the check`() {
        assertNull(LinkDetector.firstUrl("look at https://x.y@127.0.0.1/admin"))
        assertNull(LinkDetector.firstUrl("https://example.com@192.168.1.1/"))
        assertNull(LinkDetector.firstUrl("https://example.com@[::1]/"))
    }

    @Test
    fun `addresses on the device or its network are not links`() {
        assertNull(LinkDetector.firstUrl("http://127.0.0.1:8080/x"))
        assertNull(LinkDetector.firstUrl("http://10.0.0.5/x"))
        assertNull(LinkDetector.firstUrl("http://192.168.0.1/x"))
        assertNull(LinkDetector.firstUrl("http://169.254.169.254/latest/meta-data"))
        assertNull(LinkDetector.firstUrl("http://0.0.0.0/x"))
        assertNull(LinkDetector.firstUrl("http://printer.local/x"))
        assertNull(LinkDetector.firstUrl("http://db.internal/x"))
    }

    @Test
    fun `an ordinary public link still passes`() {
        assertEquals("https://example.com/a", LinkDetector.firstUrl("https://example.com/a"))
        assertEquals("https://sub.example.co.uk/a", LinkDetector.firstUrl("https://sub.example.co.uk/a"))
        // A public literal address is still a link; only private ones are not.
        assertEquals("http://93.184.216.34/x", LinkDetector.firstUrl("http://93.184.216.34/x"))
    }

    @Test
    fun `the host is parsed, lowercased and punycoded`() {
        assertEquals("example.org", LinkDetector.hostOf("http://Example.ORG/path"))
        assertEquals("evil.example", LinkDetector.hostOf("https://example.com@evil.example/x"))
        assertEquals("xn--bcher-kva.example", LinkDetector.hostOf("https://bücher.example/x"))
        assertNull(LinkDetector.hostOf("not a url"))
    }
}
