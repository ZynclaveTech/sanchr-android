package com.sanchr.core.crypto

import android.content.Context

/**
 * Test double for [StagedIdentityStore] that uses the real on-disk file
 * layout but bypasses AndroidKeyStore wrapping (Robolectric lacks an
 * `AES/GCM/NoPadding` AndroidKeyStore shadow on the SDKs we target).
 *
 * This preserves the "process restart" behavior exercised by tests — a
 * second instance pointed at the same `context.filesDir` still loads the
 * staged identity — while keeping the JVM test runner happy.
 */
class InMemoryStagedIdentityStore(
    context: Context,
) : StagedIdentityStore(context) {
    // XOR with a fixed byte so the on-disk file isn't literal plaintext — that
    // would make the test accidentally prove something weaker than the real
    // class. It's still not security; it's just to keep the test faithful to
    // "blob is opaque bytes".
    override fun wrap(plaintext: ByteArray): ByteArray = xorMask(plaintext)

    override fun unwrap(blob: ByteArray): ByteArray? = xorMask(blob)

    private fun xorMask(input: ByteArray): ByteArray {
        val out = input.copyOf()
        for (i in out.indices) {
            out[i] = (out[i].toInt() xor 0x5A).toByte()
        }
        return out
    }
}
