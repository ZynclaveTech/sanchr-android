package com.sanchr.proto.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SignedPreKey(
    @SerialName("key_id") val keyId: Int = 0,
    @SerialName("public_key") val publicKey: ByteArray = ByteArray(0),
    val signature: ByteArray = ByteArray(0),
    val timestamp: Long = 0L,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignedPreKey) return false
        return keyId == other.keyId && publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int = keyId
}

@Serializable
data class OneTimePreKey(
    @SerialName("key_id") val keyId: Int = 0,
    @SerialName("public_key") val publicKey: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OneTimePreKey) return false
        return keyId == other.keyId && publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int = keyId
}

@Serializable
data class KyberPreKey(
    @SerialName("key_id") val keyId: Int = 0,
    @SerialName("public_key") val publicKey: ByteArray = ByteArray(0),
    val signature: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KyberPreKey) return false
        return keyId == other.keyId && publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int = keyId
}

@Serializable
data class KeyBundle(
    @SerialName("identity_key") val identityKey: ByteArray = ByteArray(0),
    @SerialName("signed_pre_key") val signedPreKey: SignedPreKey? = null,
    @SerialName("one_time_pre_keys") val oneTimePreKeys: List<OneTimePreKey> = emptyList(),
    @SerialName("registration_id") val registrationId: Int = 0,
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("kyber_pre_key") val kyberPreKey: KyberPreKey? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyBundle) return false
        return deviceId == other.deviceId && identityKey.contentEquals(other.identityKey)
    }

    override fun hashCode(): Int = deviceId.hashCode()
}

@Serializable
data class UploadKeyBundleResponse(
    val success: Boolean = false,
)

@Serializable
data class GetPreKeyBundleRequest(
    @SerialName("user_id") val userId: String = "",
    @SerialName("device_id") val deviceId: String = "",
)

@Serializable
data class PreKeyBundleResponse(
    @SerialName("identity_key") val identityKey: ByteArray = ByteArray(0),
    @SerialName("signed_pre_key") val signedPreKey: SignedPreKey? = null,
    @SerialName("one_time_pre_key") val oneTimePreKey: OneTimePreKey? = null,
    @SerialName("registration_id") val registrationId: Int = 0,
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("kyber_pre_key") val kyberPreKey: KyberPreKey? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PreKeyBundleResponse) return false
        return deviceId == other.deviceId
    }

    override fun hashCode(): Int = deviceId.hashCode()
}

@Serializable
data class UploadOneTimePreKeysRequest(
    @SerialName("pre_keys") val preKeys: List<OneTimePreKey> = emptyList(),
    @SerialName("device_id") val deviceId: String = "",
)

@Serializable
data class PreKeyCountResponse(
    val count: Int = 0,
)

@Serializable
data class GetPreKeyCountRequest(
    @SerialName("device_id") val deviceId: String = "",
)

@Serializable
data class GetUserDevicesRequest(
    @SerialName("user_id") val userId: String = "",
)

@Serializable
data class GetUserDevicesResponse(
    val devices: List<DeviceInfo> = emptyList(),
)

@Serializable
data class DeviceInfo(
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("registration_id") val registrationId: Int = 0,
    val platform: String = "",
    @SerialName("last_active") val lastActive: Long = 0L,
)
