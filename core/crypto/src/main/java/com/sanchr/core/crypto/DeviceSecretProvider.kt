package com.sanchr.core.crypto

import com.sanchr.core.datastore.SessionManager
import dagger.hilt.android.scopes.ViewModelScoped
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

interface DeviceSecretProvider {
    fun getDeviceMasterSecret(): ByteArray?
    fun getOrCreateDeviceMasterSecret(): ByteArray
    fun localDatabaseKey(): ByteArray
    fun localHmacKey(): ByteArray
    fun mediaWrapKey(): ByteArray
    fun clearDeviceSecrets()
}

@Singleton
class AndroidDeviceSecretProvider @Inject constructor(
    private val sessionManager: SessionManager,
) : DeviceSecretProvider {
    private companion object {
        const val OUTPUT_BYTES = 32
        val SALT = "sanchr.device.hkdf.v1".toByteArray()
        val SQL_CIPHER_INFO = "sanchr.device.sqlcipher.v1".toByteArray()
        val HMAC_INFO = "sanchr.device.local-hmac.v1".toByteArray()
        val MEDIA_WRAP_INFO = "sanchr.device.media-wrap.v1".toByteArray()
    }

    override fun getDeviceMasterSecret(): ByteArray? = sessionManager.getDeviceMasterSecret()

    override fun getOrCreateDeviceMasterSecret(): ByteArray {
        getDeviceMasterSecret()?.let { return it }

        val secret = ByteArray(32)
        SecureRandom().nextBytes(secret)
        sessionManager.saveDeviceMasterSecret(secret)
        return secret
    }

    override fun localDatabaseKey(): ByteArray = deriveKey(SQL_CIPHER_INFO)

    override fun localHmacKey(): ByteArray = deriveKey(HMAC_INFO)

    override fun mediaWrapKey(): ByteArray = deriveKey(MEDIA_WRAP_INFO)

    override fun clearDeviceSecrets() {
        sessionManager.clearDeviceSecrets()
    }

    private fun deriveKey(info: ByteArray): ByteArray {
        val prk = hmacSha256(SALT, getOrCreateDeviceMasterSecret())
        return hkdfExpand(prk, info, OUTPUT_BYTES)
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, outputLength: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))

        val output = ByteArray(outputLength)
        var previousBlock = ByteArray(0)
        var offset = 0
        var counter = 1

        while (offset < outputLength) {
            mac.reset()
            mac.update(previousBlock)
            mac.update(info)
            mac.update(counter.toByte())
            previousBlock = mac.doFinal()

            val bytesToCopy = minOf(previousBlock.size, outputLength - offset)
            previousBlock.copyInto(output, offset, 0, bytesToCopy)
            offset += bytesToCopy
            counter += 1
        }

        return output
    }

    private fun hmacSha256(key: ByteArray, input: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(input)
    }
}
