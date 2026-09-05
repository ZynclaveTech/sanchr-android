package com.sanchr.core.network.link

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
