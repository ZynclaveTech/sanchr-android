package com.sanchr.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_sessions")
data class SignalSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "address")
    val address: String,
    @ColumnInfo(name = "session_record")
    val sessionRecord: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignalSessionEntity) return false
        return address == other.address && sessionRecord.contentEquals(other.sessionRecord)
    }

    override fun hashCode(): Int {
        var r = address.hashCode()
        r = 31 * r + sessionRecord.contentHashCode()
        return r
    }
}
