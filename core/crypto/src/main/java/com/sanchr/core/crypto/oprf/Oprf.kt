package com.sanchr.core.crypto.oprf

/**
 * The client half of the OPRF, as a seam over [OprfClient] so the discovery
 * protocol can be unit-tested without loading the native library.
 */
interface Oprf {
    /** @throws OprfException if the native layer could not blind the input. */
    fun blind(phoneE164: String): OprfBlinding

    /** @throws OprfException if either input is malformed or the point is invalid. */
    fun unblind(
        scalar: ByteArray,
        evaluated: ByteArray,
    ): ByteArray
}
