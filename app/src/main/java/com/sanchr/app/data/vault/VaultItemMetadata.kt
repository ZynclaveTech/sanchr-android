package com.sanchr.app.data.vault

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The envelope inside `VaultItem.encrypted_metadata`. Field names and
 * encoding are iOS `VaultItemMetadata` (Swift `Codable` defaults: camelCase
 * keys, `Data` as base64) so a backup restored across platforms decodes.
 */
@Serializable
data class VaultItemMetadata(
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    /** Base64 JPEG, as Swift encodes `Data`. */
    val thumbnailJpeg: String? = null,
    val originalSenderId: String? = null,
    val createdAtMs: Long,
    /** iOS `AccessKeyEntry.Kind` raw value, e.g. "vaultManual". */
    val kind: String,
) {
    fun encode(): ByteArray = JSON.encodeToString(this).toByteArray(Charsets.UTF_8)

    companion object {
        private val JSON =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = false
            }

        fun decode(bytes: ByteArray): VaultItemMetadata = JSON.decodeFromString(serializer(), String(bytes, Charsets.UTF_8))
    }
}
