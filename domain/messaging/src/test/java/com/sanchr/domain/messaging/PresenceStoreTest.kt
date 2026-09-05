package com.sanchr.domain.messaging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PresenceStoreTest {
    private var now = 1_700_000_000_000L
    private val store = PresenceStore { now }

    @Test
    fun `online decays after two missed 30-second beats`() {
        store.update("a", PresenceStatus.ONLINE, null)
        assertEquals("Online", store.statusLine("a"))
        now += 60_000
        assertEquals("Online", store.statusLine("a"))
        now += 20_000
        assertNull(store.statusLine("a"))
    }

    @Test
    fun `offline renders a relative last-seen and hidden renders nothing`() {
        store.update("a", PresenceStatus.OFFLINE, now - 3 * 60 * 60_000)
        assertEquals("Last seen 3 h ago", store.statusLine("a"))
        store.update("a", PresenceStatus.OFFLINE, now - 30_000)
        assertEquals("Last seen just now", store.statusLine("a"))
        store.update("a", PresenceStatus.OFFLINE, now - 2 * 24 * 60 * 60_000)
        assertEquals("Last seen 2 d ago", store.statusLine("a"))
        store.update("a", PresenceStatus.OFFLINE, 0)
        assertNull(store.statusLine("a"))
        store.update("a", PresenceStatus.HIDDEN, now)
        assertNull(store.statusLine("a"))
        assertNull(store.statusLine("nobody"))
    }
}
