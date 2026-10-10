package com.sanchr.domain.messaging.media

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An attachment may carry a URL the sender wrote instead of uploaded media,
 * and the downloader requests it — on auto-download, with nobody tapping
 * anything.
 */
class AttachmentLinkTest {
    @Test
    fun `a public https link is fetched`() {
        assertTrue(AttachmentDownloader.isFetchableLink("https://cdn.example.com/a.jpg"))
    }

    @Test
    fun `the device and its network are not`() {
        listOf(
            "https://127.0.0.1/a.jpg",
            "https://192.168.1.1/cgi-bin/reboot",
            "https://169.254.169.254/latest/meta-data",
            "https://printer.local/a.jpg",
            "https://localhost/a.jpg",
            "https://example.com@10.0.0.1/a.jpg",
            "https://[::1]/a.jpg",
            "https://2130706433/a.jpg",
        ).forEach { assertFalse(AttachmentDownloader.isFetchableLink(it), it) }
    }

    @Test
    fun `nothing but https is`() {
        listOf("http://cdn.example.com/a.jpg", "file:///data/data/com.sanchr/databases/x", "content://media/1", "ftp://example.com/a", "")
            .forEach { assertFalse(AttachmentDownloader.isFetchableLink(it), it) }
    }
}
