package com.sanchr.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A per-item access key (`AccessK`) the device derived and must keep to
 * read a vault item or a media attachment later. Mirrors iOS
 * `AccessKeyEntry`: [mediaId] is the vault item id for vault entries (the
 * column name is historical). Expiry is a sliding 30-day window over
 * `max(created_at, last_accessed_at)`, enforced by `AccessKeyStore`.
 */
@Entity(tableName = "access_keys")
data class AccessKeyEntity(
    @PrimaryKey
    @ColumnInfo(name = "media_id")
    val mediaId: String,
    @ColumnInfo(name = "access_key")
    val accessKey: ByteArray,
    @ColumnInfo(name = "conversation_id")
    val conversationId: String,
    /** "messageMedia", "vaultAutoVaulted" or "vaultManual" — iOS `AccessKeyEntry.Kind` raw values. */
    @ColumnInfo(name = "kind")
    val kind: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "last_accessed_at")
    val lastAccessedAt: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AccessKeyEntity) return false
        return mediaId == other.mediaId &&
            accessKey.contentEquals(other.accessKey) &&
            conversationId == other.conversationId &&
            kind == other.kind &&
            createdAt == other.createdAt &&
            lastAccessedAt == other.lastAccessedAt
    }

    override fun hashCode(): Int = mediaId.hashCode() * 31 + accessKey.contentHashCode()
}
