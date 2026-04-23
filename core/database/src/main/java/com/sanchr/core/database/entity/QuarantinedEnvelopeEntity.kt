package com.sanchr.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An incoming envelope whose decrypt failed in a way that must be surfaced to
 * the user / operator rather than silently dropped. Populated by the receive
 * pipeline when libsignal throws `InvalidMessageException`, duplicate
 * detection fires twice, or the peer session is missing and cannot be
 * recovered. Consumers page through this table via the DAO; no Flow/observer
 * path is needed in M3.
 */
@Entity(tableName = "quarantined_envelopes")
data class QuarantinedEnvelopeEntity(
    @PrimaryKey
    @ColumnInfo(name = "envelope_id")
    val envelopeId: String,
    @ColumnInfo(name = "received_at")
    val receivedAt: Long,
    @ColumnInfo(name = "payload")
    val payload: ByteArray,
    @ColumnInfo(name = "sender_user_id")
    val senderUserId: String? = null,
    @ColumnInfo(name = "sender_device")
    val senderDeviceId: Int? = null,
    @ColumnInfo(name = "failure_class")
    val failureClass: String,
    @ColumnInfo(name = "failure_message")
    val failureMessage: String? = null,
    @ColumnInfo(name = "attempts")
    val attempts: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QuarantinedEnvelopeEntity) return false
        return envelopeId == other.envelopeId &&
            receivedAt == other.receivedAt &&
            payload.contentEquals(other.payload) &&
            senderUserId == other.senderUserId &&
            senderDeviceId == other.senderDeviceId &&
            failureClass == other.failureClass &&
            failureMessage == other.failureMessage &&
            attempts == other.attempts
    }

    override fun hashCode(): Int {
        var r = envelopeId.hashCode()
        r = 31 * r + receivedAt.hashCode()
        r = 31 * r + payload.contentHashCode()
        r = 31 * r + (senderUserId?.hashCode() ?: 0)
        r = 31 * r + (senderDeviceId ?: 0)
        r = 31 * r + failureClass.hashCode()
        r = 31 * r + (failureMessage?.hashCode() ?: 0)
        r = 31 * r + attempts
        return r
    }
}
