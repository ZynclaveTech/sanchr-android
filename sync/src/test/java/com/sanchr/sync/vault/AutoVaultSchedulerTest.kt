package com.sanchr.sync.vault

import androidx.work.NetworkType
import kotlin.test.Test
import kotlin.test.assertEquals

/** Vaulting costs data twice over, so the auto-download setting decides which connection pays. */
class AutoVaultSchedulerTest {
    @Test
    fun `only the always setting lets vaulting use mobile data`() {
        assertEquals(NetworkType.CONNECTED, AutoVaultSchedulerImpl.networkFor("always"))
        assertEquals(NetworkType.UNMETERED, AutoVaultSchedulerImpl.networkFor("wifi"))
        // "Never" makes it wait for Wi-Fi; it does not undo the chat's switch.
        assertEquals(NetworkType.UNMETERED, AutoVaultSchedulerImpl.networkFor("never"))
        assertEquals(NetworkType.UNMETERED, AutoVaultSchedulerImpl.networkFor("something-new"))
    }
}
