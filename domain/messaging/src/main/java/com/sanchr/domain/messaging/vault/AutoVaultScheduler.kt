package com.sanchr.domain.messaging.vault

/**
 * Asks for a received message's media to be moved into the vault.
 *
 * Scheduled rather than done on the spot: the receive path must not wait on a
 * download and an upload, and work that is merely launched is lost if the
 * process is killed — which, for a message that arrived by push, it usually
 * is within seconds.
 */
interface AutoVaultScheduler {
    suspend fun schedule(messageId: String)
}
