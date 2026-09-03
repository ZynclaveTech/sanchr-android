package com.sanchr.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_prekeys")
data class SignalPreKeyEntity(
    @PrimaryKey
    @ColumnInfo(name = "prekey_id")
    val prekeyId: Int,
    @ColumnInfo(name = "record")
    val record: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignalPreKeyEntity) return false
        return prekeyId == other.prekeyId && record.contentEquals(other.record)
    }

    override fun hashCode(): Int {
        var r = prekeyId
        r = 31 * r + record.contentHashCode()
        return r
    }
}
