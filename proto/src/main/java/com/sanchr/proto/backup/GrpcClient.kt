package com.sanchr.proto.backup

import io.grpc.CallOptions
import io.grpc.Channel
import sanchr.backup.Backup
import sanchr.backup.BackupServiceGrpcKt

interface BackupServiceClient {
    suspend fun createBackupUpload(request: CreateBackupUploadRequest): CreateBackupUploadResponse
    suspend fun commitBackup(request: CommitBackupRequest): CommitBackupResponse
    suspend fun listBackups(request: ListBackupsRequest = ListBackupsRequest): ListBackupsResponse
    suspend fun getBackupDownload(request: GetBackupDownloadRequest): GetBackupDownloadResponse
    suspend fun deleteBackup(request: DeleteBackupRequest): DeleteBackupResponse
}

class BackupServiceGrpcClient(
    private val channel: Channel,
    private val callOptions: CallOptions = CallOptions.DEFAULT,
) : BackupServiceClient {
    private val stub = BackupServiceGrpcKt.BackupServiceCoroutineStub(channel, callOptions)

    override suspend fun createBackupUpload(request: CreateBackupUploadRequest): CreateBackupUploadResponse {
        return stub.createBackupUpload(request.toProto()).toManual()
    }

    override suspend fun commitBackup(request: CommitBackupRequest): CommitBackupResponse {
        return stub.commitBackup(request.toProto()).toManual()
    }

    override suspend fun listBackups(request: ListBackupsRequest): ListBackupsResponse {
        return stub.listBackups(Backup.ListBackupsRequest.getDefaultInstance()).toManual()
    }

    override suspend fun getBackupDownload(request: GetBackupDownloadRequest): GetBackupDownloadResponse {
        return stub.getBackupDownload(request.toProto()).toManual()
    }

    override suspend fun deleteBackup(request: DeleteBackupRequest): DeleteBackupResponse {
        stub.deleteBackup(request.toProto())
        return DeleteBackupResponse
    }
}

private fun CreateBackupUploadRequest.toProto(): Backup.CreateBackupUploadRequest =
    Backup.CreateBackupUploadRequest.newBuilder()
        .setByteSize(byteSize)
        .setSha256Hash(sha256Hash)
        .setOpaqueMetadata(com.google.protobuf.ByteString.copyFrom(opaqueMetadata))
        .setReservedForwardSecrecyMetadata(
            com.google.protobuf.ByteString.copyFrom(reservedForwardSecrecyMetadata),
        )
        .setLineageId(lineageId)
        .setFormatVersion(formatVersion)
        .build()

private fun CommitBackupRequest.toProto(): Backup.CommitBackupRequest =
    Backup.CommitBackupRequest.newBuilder()
        .setBackupId(backupId)
        .setByteSize(byteSize)
        .setSha256Hash(sha256Hash)
        .build()

private fun GetBackupDownloadRequest.toProto(): Backup.GetBackupDownloadRequest =
    Backup.GetBackupDownloadRequest.newBuilder()
        .setBackupId(backupId)
        .build()

private fun DeleteBackupRequest.toProto(): Backup.DeleteBackupRequest =
    Backup.DeleteBackupRequest.newBuilder()
        .setBackupId(backupId)
        .build()

private fun Backup.BackupMetadata.toManual(): BackupMetadata =
    BackupMetadata(
        backupId = backupId,
        lineageId = lineageId,
        formatVersion = formatVersion,
        byteSize = byteSize,
        sha256Hash = sha256Hash,
        opaqueMetadata = opaqueMetadata.toByteArray(),
        reservedForwardSecrecyMetadata = reservedForwardSecrecyMetadata.toByteArray(),
        createdAt = createdAt,
        committedAt = committedAt,
    )

private fun Backup.CreateBackupUploadResponse.toManual(): CreateBackupUploadResponse =
    CreateBackupUploadResponse(
        backupId = backupId,
        lineageId = lineageId,
        uploadUrl = uploadUrl,
        expiresIn = expiresIn,
    )

private fun Backup.CommitBackupResponse.toManual(): CommitBackupResponse =
    CommitBackupResponse(
        backup = if (hasBackup()) backup.toManual() else null,
    )

private fun Backup.ListBackupsResponse.toManual(): ListBackupsResponse =
    ListBackupsResponse(
        backups = backupsList.map(Backup.BackupMetadata::toManual),
    )

private fun Backup.GetBackupDownloadResponse.toManual(): GetBackupDownloadResponse =
    GetBackupDownloadResponse(
        backupId = backupId,
        downloadUrl = downloadUrl,
        expiresIn = expiresIn,
        backup = if (hasBackup()) backup.toManual() else null,
    )
