package com.sanchr.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A received message that decrypted but could not be filed, kept so it can be
 * filed later.
 *
 * Decrypting moves the session on: the same envelope cannot be decrypted
 * twice. So what came out of it must not be let go of until it has been
 * dealt with. When filing it failed — for any reason a database write can
 * fail — the decrypted content was dropped, and the message was gone for
 * good however many times the server sent it again.
 *
 * Holds the decrypted payload as it stood, inside the same encrypted
 * database as the messages themselves.
 */
@Entity(tableName = "held_incoming")
data class HeldIncomingEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "sender_user_id")
    val senderUserId: String,
    @ColumnInfo(name = "sender_device_id")
    val senderDeviceId: Int,
    @ColumnInfo(name = "plaintext", typeAffinity = ColumnInfo.BLOB)
    val plaintext: ByteArray,
    /** Epoch millis, from the server. */
    @ColumnInfo(name = "server_timestamp")
    val serverTimestamp: Long,
    /** The envelope's own ids and type: what the server knows it by. */
    @ColumnInfo(name = "envelope_conversation_id")
    val envelopeConversationId: String,
    @ColumnInfo(name = "envelope_message_id")
    val envelopeMessageId: String,
    @ColumnInfo(name = "content_type")
    val contentType: String,
    @ColumnInfo(name = "attempts")
    val attempts: Int = 0,
    /** Epoch millis. */
    @ColumnInfo(name = "held_at")
    val heldAt: Long,
) {
    override fun equals(other: Any?): Boolean =
        other is HeldIncomingEntity &&
            id == other.id &&
            senderUserId == other.senderUserId &&
            senderDeviceId == other.senderDeviceId &&
            plaintext.contentEquals(other.plaintext) &&
            serverTimestamp == other.serverTimestamp &&
            envelopeConversationId == other.envelopeConversationId &&
            envelopeMessageId == other.envelopeMessageId &&
            contentType == other.contentType &&
            attempts == other.attempts &&
            heldAt == other.heldAt

    override fun hashCode(): Int = 31 * id.hashCode() + plaintext.contentHashCode()
}
