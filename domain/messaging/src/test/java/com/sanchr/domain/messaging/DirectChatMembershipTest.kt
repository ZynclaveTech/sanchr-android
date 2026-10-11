package com.sanchr.domain.messaging

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Who may have a message filed in a one-to-one chat. */
class DirectChatMembershipTest {
    private fun allows(
        sender: String,
        type: String? = "DIRECT",
        participants: List<String> = listOf("me", "alice"),
        self: String? = "me",
    ) = DirectChatMembership.allows(type, participants, sender, self)

    @Test
    fun `the other person in the chat may post to it`() {
        assertTrue(allows("alice"))
    }

    /** The case this exists for: Mallory's message, filed in the chat with Alice. */
    @Test
    fun `someone who is not in the chat may not`() {
        assertFalse(allows("mallory"))
        assertFalse(allows(""))
    }

    @Test
    fun `this user's own message from another device belongs`() {
        assertTrue(allows("me"))
        assertTrue(allows("me", participants = listOf("alice")))
    }

    @Test
    fun `a list that names only the other person is enough to judge by`() {
        assertTrue(allows("alice", participants = listOf("alice")))
        assertFalse(allows("mallory", participants = listOf("alice")))
    }

    /** Saying no drops a message, so anything unsure says yes. */
    @Test
    fun `a chat whose other member is not known is not judged`() {
        assertTrue(allows("mallory", participants = emptyList()))
        assertTrue(allows("mallory", participants = listOf("me")))
        assertTrue(allows("mallory", participants = listOf("", "me")))
    }

    /** A restored backup keeps the archive's spelling of the type. */
    @Test
    fun `a one-to-one chat is one however its type is spelled`() {
        assertFalse(allows("mallory", type = "direct"))
        assertFalse(allows("mallory", type = "Direct"))
        assertTrue(allows("alice", type = "direct"))
    }

    @Test
    fun `a group is not judged here`() {
        assertTrue(allows("mallory", type = "GROUP"))
        assertTrue(allows("mallory", type = null))
    }

    @Test
    fun `with nobody signed in the sender still has to be a member`() {
        assertTrue(allows("alice", self = null))
        assertFalse(allows("mallory", self = null))
        // Not waved through for matching a blank "self".
        assertFalse(allows("", self = ""))
    }
}
