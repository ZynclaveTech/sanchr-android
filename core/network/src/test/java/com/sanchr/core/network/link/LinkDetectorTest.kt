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

    @Test
    fun `an address written in a non-standard form is not a link`() {
        // Every one of these is loopback to some resolvers and a meaningless
        // string to others. The fetcher must not be the component that finds
        // out which.
        assertNull(LinkDetector.firstUrl("http://2130706433/x"))
        assertNull(LinkDetector.firstUrl("http://0x7f.1/x"))
        assertNull(LinkDetector.firstUrl("http://127.1/x"))
        assertNull(LinkDetector.firstUrl("http://0177.0.0.1/x"))
        assertNull(LinkDetector.firstUrl("http://0x7f000001/x"))
    }

    @Test
    fun `a trailing dot does not evade the private-address check`() {
        assertNull(LinkDetector.firstUrl("http://127.0.0.1./x"))
        assertNull(LinkDetector.firstUrl("http://db.internal./x"))
        assertEquals("example.com", LinkDetector.hostOf("https://example.com./a"))
    }

    @Test
    fun `unique-local and carrier-grade-NAT addresses are not links`() {
        // fc00::/7 is the live unique-local range; isSiteLocalAddress only
        // knows the deprecated fec0::/10 it replaced.
        assertNull(LinkDetector.firstUrl("http://[fd00::1]/x"))
        assertNull(LinkDetector.firstUrl("http://[fc00::1]/x"))
        // 100.64.0.0/10 is another machine on the same carrier's network.
        assertNull(LinkDetector.firstUrl("http://100.64.0.1/x"))
        assertNull(LinkDetector.firstUrl("http://100.127.255.254/x"))
        // The octets either side of the range are ordinary public addresses.
        assertEquals("http://100.63.0.1/x", LinkDetector.firstUrl("http://100.63.0.1/x"))
        assertEquals("http://100.128.0.1/x", LinkDetector.firstUrl("http://100.128.0.1/x"))
    }

    @Test
    fun `a host with hex-looking labels is still a name`() {
        // "cafe" and "face" are hex digits; they are also words. Only hosts
        // whose every label is numeric are treated as addresses.
        assertEquals("https://face.cafe/x", LinkDetector.firstUrl("https://face.cafe/x"))
        assertEquals("https://9gag.com/x", LinkDetector.firstUrl("https://9gag.com/x"))
    }
}
