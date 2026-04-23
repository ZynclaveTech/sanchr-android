package com.sanchr.core.crypto

/**
 * libsignal-compatible message padding.
 *
 * Before a plaintext is fed into a SessionCipher / SealedSessionCipher it is
 * padded so that the on-wire ciphertext length does not leak the exact
 * plaintext length. The scheme matches Signal's reference implementation:
 *
 * 1. Append a single sentinel byte `0x80`.
 * 2. Append `0x00` bytes until the total length is a multiple of [minSize].
 *
 * On decrypt, trailing zeros are stripped and exactly one `0x80` sentinel is
 * consumed. If the sentinel is absent the payload is malformed and
 * [IllegalArgumentException] is thrown.
 */
object MessagePadding {
    /**
     * Returns `data || 0x80 || 0x00*` padded up to the next multiple of
     * [minSize]. If `data.size` is already a multiple of [minSize], a full
     * additional block is added so that the `0x80` sentinel is always present.
     */
    fun pad(
        data: ByteArray,
        minSize: Int = 160,
    ): ByteArray {
        require(minSize > 0) { "minSize must be positive" }
        // +1 for the 0x80 sentinel — if that overflows the current block,
        // roll to the next one. This also guarantees a fresh block when
        // data.size is already a multiple of minSize.
        val unpaddedWithSentinel = data.size + 1
        val padded = ((unpaddedWithSentinel + minSize - 1) / minSize) * minSize
        val out = ByteArray(padded)
        System.arraycopy(data, 0, out, 0, data.size)
        out[data.size] = 0x80.toByte()
        // remaining bytes are already zero from ByteArray(padded)
        return out
    }

    /**
     * Strips libsignal-style padding. Scans from the end, skipping `0x00`
     * bytes, and consumes exactly one `0x80` sentinel. Everything before the
     * sentinel is returned unchanged.
     *
     * @throws IllegalArgumentException if no `0x80` sentinel is found.
     */
    fun strip(padded: ByteArray): ByteArray {
        var i = padded.size - 1
        while (i >= 0 && padded[i] == 0x00.toByte()) {
            i--
        }
        require(i >= 0 && padded[i] == 0x80.toByte()) {
            "Padded plaintext missing 0x80 sentinel"
        }
        return padded.copyOfRange(0, i)
    }
}
