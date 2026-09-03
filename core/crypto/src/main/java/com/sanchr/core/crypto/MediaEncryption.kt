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
