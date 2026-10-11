package com.sanchr.domain.messaging

/**
 * Whether a message may be filed in the conversation it names.
 *
 * A sealed message names its own conversation, inside a payload its sender
 * wrote; nothing the server stamped says where it belongs. Filed on the
 * sender's word alone, a message from one person could be put into this
 * user's chat with another — where a one-to-one chat shows every incoming
 * bubble as that other person's.
 *
 * Deliberately narrow, because the answer "no" drops a message. It refuses
 * only what is certainly wrong: a one-to-one chat this phone knows the other
 * member of, and a sender who is neither of the two. A group, a chat this
 * phone has not seen yet, or one whose other member it has not been told is
 * let through, as before.
 */
object DirectChatMembership {
    const val DIRECT = "DIRECT"

    fun allows(
        conversationType: String?,
        participantIds: List<String>,
        senderId: String,
        selfId: String?,
    ): Boolean {
        // Not always upper case in the database: a restored backup keeps
        // whatever spelling the archive had.
        if (!conversationType.equals(DIRECT, ignoreCase = true)) return true
        // Our own message, echoed from another of our devices.
        if (!selfId.isNullOrBlank() && senderId == selfId) return true
        val others = participantIds.filter { it.isNotBlank() && it != selfId }
        return others.isEmpty() || senderId in others
    }
}
