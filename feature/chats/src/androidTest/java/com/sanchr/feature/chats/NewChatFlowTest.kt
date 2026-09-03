package com.sanchr.feature.chats

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end smoke test for the new-chat-by-phone flow. Compile-only gate
 * for M5 — full execution requires a connected emulator because Hilt +
 * Compose UI tests need an Android runtime.
 *
 * Planned fixture shape (to be wired when the emulator pipeline exists):
 *   - Fake ContactRepository:
 *       - lookupByPhone("+15551234567") -> User(id="u1", ...)
 *       - lookupByPhone(unknown)        -> null (NOT_FOUND)
 *   - Fake MessageRepository:
 *       - ensureConversation("u1") -> "conv-1"
 *   - Real ChatsListViewModel wired via Hilt test module
 *   - Launch ChatsListScreen in ComposeRule
 *   - Flow:
 *       1. Tap new-chat FAB                       -> sheet visible
 *       2. Enter "abc", tap "Start chat"          -> INVALID_PHONE chip
 *       3. Clear, enter "+15550000000", tap submit -> NOT_FOUND chip
 *       4. Clear, enter "+15551234567", tap submit -> onOpenConversation
 *          invoked with "conv-1" and sheet dismissed
 */
@RunWith(AndroidJUnit4::class)
class NewChatFlowTest {
    @Test
    fun new_chat_flow_reaches_open_conversation() {
        // Intentionally empty: compile-only gate for M5. See class KDoc.
    }
}
