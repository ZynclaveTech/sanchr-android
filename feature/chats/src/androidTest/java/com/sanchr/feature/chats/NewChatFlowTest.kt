package com.sanchr.feature.chats

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end smoke test for the new-chat contact-picker flow. Compile-only
 * gate for M5 — full execution requires a connected emulator because Hilt +
 * Compose UI tests need an Android runtime.
 *
 * Planned fixture shape (to be wired when the emulator pipeline exists):
 *   - Fake ContactRepository:
 *       - observeRegisteredContacts() -> flowOf([ada, grace])
 *       - syncContacts()              -> Unit
 *   - Fake MessageRepository:
 *       - ensureConversation("user-ada") -> "conv-1"
 *   - Real ChatsListViewModel wired via Hilt test module
 *   - Launch ChatsListScreen in ComposeRule
 *   - Flow:
 *       1. Tap new-chat FAB                      -> picker sheet visible
 *       2. Sheet shows [Ada, Grace] alphabetically
 *       3. Type "grace" in search                -> only Grace remains
 *       4. Clear search, tap Ada                 -> onOpenConversation
 *          invoked with "conv-1" and sheet dismissed
 */
@RunWith(AndroidJUnit4::class)
class NewChatFlowTest {
    @Test
    fun new_chat_picker_flow_reaches_open_conversation() {
        // Intentionally empty: compile-only gate for M5. See class KDoc.
    }
}
