package com.sanchr.core.crypto

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles AES-256-GCM encryption/decryption for media files (images, videos, documents).
 *
 * Each media file gets a unique random 256-bit key. The key is then encrypted
 * within the Signal session (as part of the message payload) so only the intended
 * recipient can decrypt the media.
 *
 * This is a stateless utility object -- all operations are pure functions that
 * take explicit key material as parameters.
 */
object MediaEncryptor {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val AES_KEY_SIZE = 256
    private const val GCM_NONCE_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val STREAM_BUFFER_SIZE = 8192

    /**
     * Generates a random 256-bit (32-byte) AES key for encrypting a single media file.
     */
    fun generateMediaKey(): ByteArray {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(AES_KEY_SIZE, SecureRandom())
        return keyGenerator.generateKey().encoded
    }

    /**
     * Encrypts data in-memory using AES-256-GCM.
     *
     * @param data The plaintext data to encrypt.
     * @param key The 32-byte AES key.
     * @return [EncryptedMediaResult] containing ciphertext, nonce, and GCM tag.
     */
    fun encrypt(
        data: ByteArray,
        key: ByteArray,
    ): EncryptedMediaResult {
        require(key.size == 32) { "AES key must be 32 bytes, got ${key.size}" }

        val nonce = ByteArray(GCM_NONCE_LENGTH).also { SecureRandom().nextBytes(it) }
        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, nonce))

        val ciphertextWithTag = cipher.doFinal(data)

        // AES-GCM appends the tag to the ciphertext. Split them.
        val tagSizeBytes = GCM_TAG_LENGTH / 8
        val ciphertext = ciphertextWithTag.copyOfRange(0, ciphertextWithTag.size - tagSizeBytes)
        val tag = ciphertextWithTag.copyOfRange(ciphertextWithTag.size - tagSizeBytes, ciphertextWithTag.size)

        return EncryptedMediaResult(
            ciphertext = ciphertext,
            nonce = nonce,
            tag = tag,
        )
    }

    /**
     * Decrypts data that was encrypted with [encrypt].
     *
     * @param ciphertext The encrypted data (without nonce or tag).
     * @param key The 32-byte AES key.
     * @param nonce The 12-byte GCM nonce used during encryption.
     * @return The decrypted plaintext.
     */
    fun decrypt(
        ciphertext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
    ): ByteArray {
        require(key.size == 32) { "AES key must be 32 bytes, got ${key.size}" }
        require(nonce.size == GCM_NONCE_LENGTH) { "Nonce must be $GCM_NONCE_LENGTH bytes" }

        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, nonce))
        return cipher.doFinal(ciphertext)
    }

    /**
     * `nonce || ciphertext || tag` in one buffer — CryptoKit's
     * `AES.GCM.SealedBox.combined`, which is what iOS writes for vault
     * payloads and metadata envelopes.
     */
    fun sealCombined(
        data: ByteArray,
        key: ByteArray,
    ): ByteArray {
        val r = encrypt(data, key)
        return r.nonce + r.ciphertext + r.tag
    }

    /** Inverse of [sealCombined]. Throws on a short buffer or a failed authentication. */
    fun openCombined(
        combined: ByteArray,
        key: ByteArray,
    ): ByteArray {
        require(combined.size >= GCM_NONCE_LENGTH + GCM_TAG_LENGTH / 8) { "combined ciphertext too short: ${combined.size}" }
        val nonce = combined.copyOfRange(0, GCM_NONCE_LENGTH)
        return decrypt(combined.copyOfRange(GCM_NONCE_LENGTH, combined.size), key, nonce)
    }

    /**
     * iOS `MediaEncryptor` seals files over 1 MiB as a sequence of
     * independently sealed 1 MiB chunks, each `nonce || ct || tag`,
     * concatenated. [openCombined] cannot read that; this walks the
     * chunks. Chunk boundaries are fixed by the plaintext chunk size, so
     * only the final chunk may be shorter.
     */
    fun openChunked(
        combined: ByteArray,
        key: ByteArray,
    ): ByteArray {
        val out = java.io.ByteArrayOutputStream(combined.size)
        var offset = 0
        while (offset < combined.size) {
            val end = minOf(offset + CHUNK_COMBINED_SIZE, combined.size)
            out.write(openCombined(combined.copyOfRange(offset, end), key))
            offset = end
        }
        return out.toByteArray()
    }

    /**
     * Opens a blob written by either platform: a single sealed box, or the
     * chunked layout iOS uses past 1 MiB. Single-shot is tried first, as
     * iOS does, so the two never disagree about a blob.
     */
    fun openAny(
        combined: ByteArray,
        key: ByteArray,
    ): ByteArray =
        try {
            openCombined(combined, key)
        } catch (e: Exception) {
            if (combined.size <= CHUNK_COMBINED_SIZE) throw e
            openChunked(combined, key)
        }

    /** Seals like iOS would for the size: single box up to 1 MiB, chunked above. */
    fun sealAny(
        data: ByteArray,
        key: ByteArray,
    ): ByteArray {
        if (data.size <= CHUNK_SIZE) return sealCombined(data, key)
        val out = java.io.ByteArrayOutputStream(data.size + (data.size / CHUNK_SIZE + 1) * (GCM_NONCE_LENGTH + GCM_TAG_LENGTH / 8))
        var offset = 0
        while (offset < data.size) {
            val end = minOf(offset + CHUNK_SIZE, data.size)
            out.write(sealCombined(data.copyOfRange(offset, end), key))
            offset = end
        }
        return out.toByteArray()
    }

    /** iOS `MediaEncryptor.chunkSize`. */
    const val CHUNK_SIZE = 1_048_576
    private const val CHUNK_COMBINED_SIZE = GCM_NONCE_LENGTH + CHUNK_SIZE + GCM_TAG_LENGTH / 8

    /**
     * Encrypts a stream for large files. Writes nonce first, then encrypted data.
     * Also computes the SHA-256 digest of the plaintext for integrity verification.
     *
     * @param input Source plaintext stream.
     * @param output Destination encrypted stream.
     * @param key The 32-byte AES key.
     * @return [MediaEncryptionMetadata] containing the key, nonce, digest, and file size.
     */
    fun encryptStream(
        input: InputStream,
        output: OutputStream,
        key: ByteArray,
    ): MediaEncryptionMetadata {
        require(key.size == 32) { "AES key must be 32 bytes, got ${key.size}" }

        val nonce = ByteArray(GCM_NONCE_LENGTH).also { SecureRandom().nextBytes(it) }
        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, nonce))

        val digest = MessageDigest.getInstance("SHA-256")
        var fileSize = 0L

        // Write nonce as the first 12 bytes of the output
        output.write(nonce)

        CipherOutputStream(output, cipher).use { cipherOut ->
            val buffer = ByteArray(STREAM_BUFFER_SIZE)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
                cipherOut.write(buffer, 0, bytesRead)
                fileSize += bytesRead
            }
        }

        return MediaEncryptionMetadata(
            key = key,
            nonce = nonce,
            digest = digest.digest(),
            fileSize = fileSize,
        )
    }

    /**
     * Decrypts a stream that was encrypted with [encryptStream].
     * Reads the nonce from the first 12 bytes of the input.
     *
     * @param input Source encrypted stream (nonce + ciphertext).
     * @param output Destination plaintext stream.
     * @param metadata The [MediaEncryptionMetadata] from encryption (provides key).
     */
    fun decryptStream(
        input: InputStream,
        output: OutputStream,
        metadata: MediaEncryptionMetadata,
    ) {
        val nonce = ByteArray(GCM_NONCE_LENGTH)
        val bytesRead = input.read(nonce)
        require(bytesRead == GCM_NONCE_LENGTH) { "Failed to read nonce from encrypted stream" }

        val secretKey = SecretKeySpec(metadata.key, "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, nonce))

        CipherInputStream(input, cipher).use { cipherIn ->
            cipherIn.copyTo(output, bufferSize = STREAM_BUFFER_SIZE)
        }
    }
}

/**
 * Result of encrypting a media file in memory.
 */
data class EncryptedMediaResult(
    /** The encrypted data (without nonce or tag). */
    val ciphertext: ByteArray,
    /** The 12-byte GCM nonce. */
    val nonce: ByteArray,
    /** The 16-byte GCM authentication tag. */
    val tag: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedMediaResult) return false
        return ciphertext.contentEquals(other.ciphertext) &&
            nonce.contentEquals(other.nonce) &&
            tag.contentEquals(other.tag)
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + tag.contentHashCode()
        return result
    }
}

/**
 * Metadata produced during stream encryption, needed for decryption and
 * integrity verification.
 */
data class MediaEncryptionMetadata(
    /** The 32-byte AES key used for encryption. */
    val key: ByteArray,
    /** The 12-byte GCM nonce. */
    val nonce: ByteArray,
    /** SHA-256 digest of the original plaintext for integrity verification. */
    val digest: ByteArray,
    /** Original plaintext file size in bytes. */
    val fileSize: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaEncryptionMetadata) return false
        return key.contentEquals(other.key) && digest.contentEquals(other.digest)
    }

    override fun hashCode(): Int = key.contentHashCode() * 31 + digest.contentHashCode()
}
