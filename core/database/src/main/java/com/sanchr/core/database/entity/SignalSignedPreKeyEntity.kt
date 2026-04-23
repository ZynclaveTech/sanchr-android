package com.sanchr.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_signed_prekeys")
data class SignalSignedPreKeyEntity(
    @PrimaryKey
    @ColumnInfo(name = "prekey_id")
    val prekeyId: Int,
    @ColumnInfo(name = "record")
    val record: ByteArray,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignalSignedPreKeyEntity) return false
        return prekeyId == other.prekeyId &&
            record.contentEquals(other.record) &&
            createdAt == other.createdAt
    }

    override fun hashCode(): Int {
        var r = prekeyId
        r = 31 * r + record.contentHashCode()
        r = 31 * r + createdAt.hashCode()
        return r
    }
}
