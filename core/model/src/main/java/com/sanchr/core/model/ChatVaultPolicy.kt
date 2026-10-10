package com.sanchr.core.model

import kotlinx.serialization.Serializable

/**
 * How one conversation treats media, as iOS `ChatVaultPolicy`.
 *
 * Everything defaults to off and nothing is merged from an account-wide
 * setting: a chat behaves like every other chat until the user changes it for
 * that chat. Device-only — nothing on the wire carries it.
 */
@Serializable
data class ChatVaultPolicy(
    /** Received media is moved into the vault and leaves a marker in the chat. */
    val autoVaultIncoming: Boolean = false,
    /** Media sent from this chat is view-once unless the sender says otherwise. */
    val viewOnceOutgoing: Boolean = false,
    /** Media from this chat cannot be screenshotted or recorded while open. */
    val captureProtection: Boolean = false,
) {
    companion object {
        val DEFAULT = ChatVaultPolicy()
    }
}
