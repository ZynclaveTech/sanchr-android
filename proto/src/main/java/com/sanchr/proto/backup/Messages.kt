package com.sanchr.proto.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BackupMetadata(
    @SerialName("backup_id") val backupId: String = "",
    @SerialName("lineage_id") val lineageId: String = "",
    @SerialName("format_version") val formatVersion: Int = 0,
    @SerialName("byte_size") val byteSize: Long = 0L,
    @SerialName("sha256_hash") val sha256Hash: String = "",
    @SerialName("opaque_metadata") val opaqueMetadata: ByteArray = byteArrayOf(),
    @SerialName("reserved_forward_secrecy_metadata")
    val reservedForwardSecrecyMetadata: ByteArray = byteArrayOf(),
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("committed_at") val committedAt: String = "",
)

@Serializable
data class CreateBackupUploadRequest(
    @SerialName("byte_size") val byteSize: Long = 0L,
    @SerialName("sha256_hash") val sha256Hash: String = "",
    @SerialName("opaque_metadata") val opaqueMetadata: ByteArray = byteArrayOf(),
    @SerialName("reserved_forward_secrecy_metadata")
    val reservedForwardSecrecyMetadata: ByteArray = byteArrayOf(),
    @SerialName("lineage_id") val lineageId: String = "",
    @SerialName("format_version") val formatVersion: Int = 1,
)

@Serializable
data class CreateBackupUploadResponse(
    @SerialName("backup_id") val backupId: String = "",
    @SerialName("lineage_id") val lineageId: String = "",
    @SerialName("upload_url") val uploadUrl: String = "",
    @SerialName("expires_in") val expiresIn: Long = 0L,
)

@Serializable
data class CommitBackupRequest(
    @SerialName("backup_id") val backupId: String = "",
    @SerialName("byte_size") val byteSize: Long = 0L,
    @SerialName("sha256_hash") val sha256Hash: String = "",
)

@Serializable
data class CommitBackupResponse(
    val backup: BackupMetadata? = null,
)

@Serializable
data object ListBackupsRequest

@Serializable
data class ListBackupsResponse(
    val backups: List<BackupMetadata> = emptyList(),
)

@Serializable
data class GetBackupDownloadRequest(
    @SerialName("backup_id") val backupId: String = "",
)

@Serializable
data class GetBackupDownloadResponse(
    @SerialName("backup_id") val backupId: String = "",
    @SerialName("download_url") val downloadUrl: String = "",
    @SerialName("expires_in") val expiresIn: Long = 0L,
    val backup: BackupMetadata? = null,
)

@Serializable
data class DeleteBackupRequest(
    @SerialName("backup_id") val backupId: String = "",
)

@Serializable
data object DeleteBackupResponse
