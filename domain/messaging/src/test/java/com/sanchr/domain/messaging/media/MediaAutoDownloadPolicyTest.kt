package com.sanchr.domain.messaging.media

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaAutoDownloadPolicyTest {
    @Test
    fun `wifi only holds back on a metered network and downloads on an unmetered one`() {
        assertFalse(MediaAutoDownloadPolicy.shouldAutoDownload("wifi", isMetered = true))
        assertTrue(MediaAutoDownloadPolicy.shouldAutoDownload("wifi", isMetered = false))
    }

    @Test
    fun `always downloads even on mobile data`() {
        assertTrue(MediaAutoDownloadPolicy.shouldAutoDownload("always", isMetered = true))
    }

    @Test
    fun `never holds back even on wifi`() {
        assertFalse(MediaAutoDownloadPolicy.shouldAutoDownload("never", isMetered = false))
    }

    @Test
    fun `an unrecognised setting behaves like the shipped default`() {
        assertFalse(MediaAutoDownloadPolicy.shouldAutoDownload("something-newer", isMetered = true))
        assertTrue(MediaAutoDownloadPolicy.shouldAutoDownload("something-newer", isMetered = false))
    }
}
