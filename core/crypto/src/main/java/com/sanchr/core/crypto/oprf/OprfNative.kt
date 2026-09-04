package com.sanchr.core.crypto.oprf

/**
 * JNI surface of `libsanchr_psi_jni`, built from the backend's
 * `crates/sanchr-psi-jni`. Both platforms call the same Rust: iOS links
 * `sanchr-psi` statically, Android loads this shared library. Nothing is
 * reimplemented in Kotlin, which is what keeps the two clients byte-compatible
 * with each other and with the server — `hash_to_point` is deliberately
 * non-standard, so a Kotlin port built on a standards-compliant hash-to-curve
 * would compile, run and match nothing.
 *
 * This object deliberately does **not** load the library. Loading is
 * [OprfNativeLoader]'s job, so a host JVM test can substitute a `.dylib` built
 * for the development machine and exercise these exact symbols.
 *
 * Symbol names are fixed by the `@JvmStatic` + `object` combination:
 * `Java_com_sanchr_core_crypto_oprf_OprfNative_blind` / `_unblind`. Renaming
 * this object or its package breaks the binding.
 */
object OprfNative {
    /**
     * Blinds a phone number: returns 64 bytes, the 32-byte blinding scalar
     * followed by the 32-byte blinded point, or null if blinding failed.
     * The scalar must never leave the device.
     */
    @JvmStatic
    external fun blind(phoneE164: String): ByteArray?

    /**
     * Unblinds the server's evaluated point with the scalar kept from [blind].
     * Returns the 32-byte result, or null if either input is not a valid
     * 32-byte encoding. Errors are reported as null rather than thrown so
     * malformed network input cannot unwind through the JNI boundary.
     */
    @JvmStatic
    external fun unblind(
        scalar: ByteArray,
        evaluated: ByteArray,
    ): ByteArray?
}
