package com.sanchr.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_identities")
data class SignalIdentityEntity(
    @PrimaryKey
    @ColumnInfo(name = "address")
    val address: String,
    @ColumnInfo(name = "identity_key")
    val identityKey: ByteArray,
    @ColumnInfo(name = "trust_level")
    val trustLevel: Int,
    @ColumnInfo(name = "first_seen_at")
    val firstSeenAt: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignalIdentityEntity) return false
        return address == other.address &&
            identityKey.contentEquals(other.identityKey) &&
            trustLevel == other.trustLevel &&
            firstSeenAt == other.firstSeenAt
    }

    override fun hashCode(): Int {
        var r = address.hashCode()
        r = 31 * r + identityKey.contentHashCode()
        r = 31 * r + trustLevel
        r = 31 * r + firstSeenAt.hashCode()
        return r
    }
}
