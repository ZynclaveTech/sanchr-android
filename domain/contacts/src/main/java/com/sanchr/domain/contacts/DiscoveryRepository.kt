package com.sanchr.domain.contacts

/**
 * Privacy-preserving contact discovery: which of these phone numbers belong
 * to registered users, without telling the server which numbers were asked.
 *
 * Every number is blinded on-device, the server evaluates the blinded points
 * under its secret, and the client unblinds and compares against the server's
 * registered set. The server sees blinded points only.
 *
 * **Fails closed.** Any error — network, rate limit, a server on a different
 * key epoch that will not settle, a malformed response — propagates. There is
 * deliberately no fallback to uploading hashes of the address book: that would
 * leak exactly what this exists to protect, and it is better to fail the sync.
 */
interface DiscoveryRepository {
    /**
     * @param phoneNumbersE164 candidate numbers in E.164 (`+…`), byte-identical
     *   to how users registered — the server does no normalisation.
     * @return the subset of the input that is registered, as the original
     *   strings. Order is not significant. Numbers the native layer could not
     *   blind are silently absent, never misattributed.
     */
    suspend fun discoverRegistered(phoneNumbersE164: List<String>): List<String>
}
