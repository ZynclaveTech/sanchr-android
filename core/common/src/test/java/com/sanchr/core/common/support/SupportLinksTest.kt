package com.sanchr.core.common.support

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SupportLinksTest {
    @Test
    fun `the documents both stores require are reachable over https`() {
        listOf(SupportLinks.PRIVACY_POLICY, SupportLinks.TERMS_OF_SERVICE, SupportLinks.DOCUMENTATION).forEach { url ->
            assertTrue(url.startsWith("https://"), "$url must be https")
        }
        assertTrue(SupportLinks.SUPPORT_EMAIL.contains('@'))
        assertTrue(SupportLinks.SECURITY_EMAIL.contains('@'))
    }

    @Test
    fun `the device report names the phone and app, and nothing about the account`() {
        val report = SupportLinks.deviceReport("OnePlus", "CPH2619", "16", 36, "1.2.3 (45)")

        assertEquals("---\nDevice: OnePlus CPH2619 (Android 16, API 36)\nApp: Sanchr 1.2.3 (45)", report)
    }
}
