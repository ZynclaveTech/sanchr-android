package com.sanchr.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "envelope_queue")
data class EnvelopeQueueEntity(
    @PrimaryKey
    @ColumnInfo(name = "envelope_id")
    val envelopeId: String,
    @ColumnInfo(name = "received_at")
    val receivedAt: Long,
    @ColumnInfo(name = "processed")
    val processed: Boolean = false,
    @ColumnInfo(name = "attempts")
    val attempts: Int = 0,
    @ColumnInfo(name = "payload")
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EnvelopeQueueEntity) return false
        return envelopeId == other.envelopeId &&
            receivedAt == other.receivedAt &&
            processed == other.processed &&
            attempts == other.attempts &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var r = envelopeId.hashCode()
        r = 31 * r + receivedAt.hashCode()
        r = 31 * r + processed.hashCode()
        r = 31 * r + attempts
        r = 31 * r + payload.contentHashCode()
        return r
    }
}
