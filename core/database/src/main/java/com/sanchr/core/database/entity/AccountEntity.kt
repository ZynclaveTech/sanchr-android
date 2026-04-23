package com.sanchr.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    @ColumnInfo(name = "phone_e164")
    val phoneE164: String,
    @ColumnInfo(name = "registration_id")
    val registrationId: Int,
    @ColumnInfo(name = "identity_private_key")
    val identityPrivateKey: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AccountEntity) return false
        return userId == other.userId &&
            deviceId == other.deviceId &&
            phoneE164 == other.phoneE164 &&
            registrationId == other.registrationId &&
            (identityPrivateKey?.contentEquals(other.identityPrivateKey) ?: (other.identityPrivateKey == null))
    }

    override fun hashCode(): Int {
        var r = userId.hashCode()
        r = 31 * r + deviceId.hashCode()
        r = 31 * r + phoneE164.hashCode()
        r = 31 * r + registrationId
        r = 31 * r + (identityPrivateKey?.contentHashCode() ?: 0)
        return r
    }
}
