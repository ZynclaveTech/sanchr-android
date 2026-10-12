package com.sanchr.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The tab bar is on the first screen of each tab, and nowhere else. */
class BottomBarVisibilityTest {
    private val tabs = TopLevelDestination.entries.map { it.route }

    private fun shows(
        route: String?,
        parent: String?,
        parentStart: String?,
    ) = isFirstScreenOfATab(route, parent, parentStart, tabs)

    @Test
    fun `the first screen of a tab has it`() {
        assertTrue(shows("chats_list", "chats_tab", "chats_list"))
        assertTrue(shows("settings_main", "settings_tab", "settings_main"))
    }

    /** It sat under the message field of every open chat. */
    @Test
    fun `a chat does not, though it lives inside the Chats tab`() {
        assertFalse(shows("chat_detail/{conversationId}", "chats_tab", "chats_list"))
    }

    @Test
    fun `nor does a screen further into Settings`() {
        assertFalse(shows("settings_security", "settings_tab", "settings_main"))
    }

    @Test
    fun `nor does anything outside the tabs`() {
        assertFalse(shows("login", "auth", "login"))
        assertFalse(shows("vault", "vault_graph", "vault"))
        assertFalse(shows(null, null, null))
        assertFalse(shows("chats_list", null, null))
    }
}
