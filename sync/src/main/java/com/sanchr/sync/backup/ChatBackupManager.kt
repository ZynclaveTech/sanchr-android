package com.sanchr.sync.backup

import android.content.Context
import androidx.room.withTransaction
import com.sanchr.core.crypto.BackupKeyDeriver
import com.sanchr.core.crypto.DerivedBackupMaterial
import com.sanchr.core.crypto.RecoveryKeyManager
import com.sanchr.core.crypto.SignalKeyManager
import com.sanchr.core.crypto.store.SanchrSignalProtocolStore
import com.sanchr.core.database.SanchrDatabase
import com.sanchr.core.database.dao.ContactDao
import com.sanchr.core.database.dao.ConversationDao
import com.sanchr.core.database.dao.MessageDao
import com.sanchr.core.database.dao.PendingMessageAckDao
import com.sanchr.core.database.entity.ContactEntity
import com.sanchr.core.database.entity.ConversationEntity
import com.sanchr.core.database.entity.MessageEntity
import com.sanchr.core.datastore.BackupConfiguration
import com.sanchr.core.datastore.SessionManager
import com.sanchr.proto.backup.BackupMetadata
import com.sanchr.proto.backup.BackupServiceClient
import com.sanchr.proto.backup.CommitBackupRequest
import com.sanchr.proto.backup.CreateBackupUploadRequest
import com.sanchr.proto.backup.DeleteBackupRequest
import com.sanchr.proto.backup.GetBackupDownloadRequest
import com.sanchr.proto.backup.ListBackupsRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
private data class BackupOpaqueMetadata(
    val formatVersion: Int,
    val ivBase64: String,
    val hmacBase64: String,
    val exportedAtMs: Long,
    val contentHash: String,
    val platform: String,
    val appVersion: String,
    val counts: BackupArchiveRecordCounts,
)

data class BackupUploadOutcome(
    val backupAtMillis: Long,
    val contentHash: String,
)

data class BackupRestoreOutcome(
    val lineageId: String,
    val formatVersion: Int,
    val backupAtMillis: Long?,
    val contentHash: String?,
)

@Singleton
class ChatBackupManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val backupServiceClient: BackupServiceClient,
        private val backupKeyDeriver: BackupKeyDeriver,
        private val recoveryKeyManager: RecoveryKeyManager,
        private val sessionManager: SessionManager,
        private val database: SanchrDatabase,
        private val conversationDao: ConversationDao,
        private val messageDao: MessageDao,
        private val contactDao: ContactDao,
        private val pendingMessageAckDao: PendingMessageAckDao,
        private val signalProtocolStore: SanchrSignalProtocolStore,
        private val signalKeyManager: SignalKeyManager,
    ) {
        companion object {
            private const val TAG = "ChatBackupManager"
        }

        private val mutex = Mutex()

        suspend fun performScheduledBackupIfNeeded(): BackupUploadOutcome? =
            mutex.withLock {
                val configuration = recoveryKeyManager.loadConfiguration() ?: return@withLock null
                val recoveryKey = recoveryKeyManager.readRecoveryKey() ?: return@withLock null
                val material = deriveMaterial(recoveryKey)
                performBackup(configuration, material, force = false)
            }

        suspend fun backupNow(): BackupUploadOutcome? =
            mutex.withLock {
                val configuration =
                    recoveryKeyManager.loadConfiguration()
                        ?: error("Backups are not enabled on this device.")
                val recoveryKey =
                    recoveryKeyManager.readRecoveryKey()
                        ?: error("Recovery key is unavailable.")
                val material = deriveMaterial(recoveryKey)
                performBackup(configuration, material, force = true)
            }

        suspend fun restoreLatestBackup(recoveryKeyOverride: String?): BackupRestoreOutcome =
            mutex.withLock {
                val recoveryKey =
                    recoveryKeyOverride?.trim()?.takeIf { it.isNotEmpty() }
                        ?: recoveryKeyManager.readRecoveryKey()
                        ?: error("Recovery key is unavailable.")
                val material = deriveMaterial(recoveryKey)
                val configuration = recoveryKeyManager.loadConfiguration()

                val backups = backupServiceClient.listBackups(ListBackupsRequest).backups
                val selected =
                    selectLatestBackup(backups, configuration?.lineageId)
                        ?: error("No encrypted backup is available for this account.")

                val download =
                    backupServiceClient.getBackupDownload(
                        GetBackupDownloadRequest(backupId = selected.backupId),
                    )
                val ciphertext = httpDownload(download.downloadUrl)
                val expectedSha = download.backup?.sha256Hash?.takeIf { it.isNotBlank() } ?: selected.sha256Hash
                check(sha256Hex(ciphertext) == expectedSha) { "Backup SHA-256 mismatch" }

                val metadataBytes = download.backup?.opaqueMetadata ?: selected.opaqueMetadata
                val metadata =
                    BackupArchive.json.decodeFromString(
                        BackupOpaqueMetadata.serializer(),
                        metadataBytes.decodeToString(),
                    )
                val iv = android.util.Base64.decode(metadata.ivBase64, android.util.Base64.NO_WRAP)
                val expectedHmac = android.util.Base64.decode(metadata.hmacBase64, android.util.Base64.NO_WRAP)
                check(hmac(iv, ciphertext, material.hmacKey ?: error("Backup HMAC key unavailable")).contentEquals(expectedHmac)) {
                    "Backup HMAC verification failed"
                }

                val plaintext = aesCbcDecrypt(ciphertext, material.aesKey ?: error("Backup AES key unavailable"), iv)
                val snapshot = BackupArchiveSerializer.deserialize(plaintext)
                restoreSnapshot(snapshot)

                recoveryKeyManager.persistRestoredBackup(
                    recoveryKey = recoveryKey,
                    lineageId = selected.lineageId,
                    formatVersion = selected.formatVersion,
                    lastBackupAtMillis = parseInstantMillis(selected.committedAt.ifBlank { selected.createdAt }),
                    lastBackupContentHash = metadata.contentHash,
                )

                signalProtocolStore.wipeAll()
                signalKeyManager.generateIdentity()
                signalKeyManager.uploadInitialKeyBundle()

                BackupRestoreOutcome(
                    lineageId = selected.lineageId,
                    formatVersion = selected.formatVersion,
                    backupAtMillis = parseInstantMillis(selected.committedAt.ifBlank { selected.createdAt }),
                    contentHash = metadata.contentHash,
                )
            }

        suspend fun deleteRemoteBackups() =
            mutex.withLock {
                val lineageId = recoveryKeyManager.loadConfiguration()?.lineageId
                val backups =
                    backupServiceClient.listBackups(ListBackupsRequest).backups.filter { backup ->
                        lineageId.isNullOrEmpty() || backup.lineageId == lineageId
                    }
                backups.forEach { backup ->
                    backupServiceClient.deleteBackup(DeleteBackupRequest(backupId = backup.backupId))
                }
                recoveryKeyManager.updateBackupState(timestampMillis = null, contentHash = null)
            }

        private suspend fun performBackup(
            configuration: BackupConfiguration,
            material: DerivedBackupMaterial,
            force: Boolean,
        ): BackupUploadOutcome? {
            val snapshot = exportSnapshot()
            val archive = BackupArchiveSerializer.serialize(snapshot)
            val contentHash = sha256Hex(archive)

            if (!force && configuration.lastBackupContentHash == contentHash) {
                return null
            }
            val lastBackupAtMillis = configuration.lastBackupAtMillis
            if (!force && lastBackupAtMillis != null) {
                val elapsed = System.currentTimeMillis() - lastBackupAtMillis
                if (elapsed < BackupArchive.AUTOMATIC_BACKUP_INTERVAL_MS) {
                    return null
                }
            }

            val aesKey = material.aesKey ?: error("Backup AES key unavailable")
            val hmacKey = material.hmacKey ?: error("Backup HMAC key unavailable")
            val iv = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
            val ciphertext = aesCbcEncrypt(archive, aesKey, iv)
            val metadata =
                BackupOpaqueMetadata(
                    formatVersion = BackupArchive.FORMAT_VERSION,
                    ivBase64 = android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP),
                    hmacBase64 =
                        android.util.Base64.encodeToString(
                            hmac(iv, ciphertext, hmacKey),
                            android.util.Base64.NO_WRAP,
                        ),
                    exportedAtMs = snapshot.info.exportedAtMs,
                    contentHash = contentHash,
                    platform = snapshot.info.platform,
                    appVersion = snapshot.info.appVersion,
                    counts = snapshot.counts,
                )
            val metadataBytes =
                BackupArchive.json
                    .encodeToString(
                        BackupOpaqueMetadata.serializer(),
                        metadata,
                    ).encodeToByteArray()

            val create =
                backupServiceClient.createBackupUpload(
                    CreateBackupUploadRequest(
                        byteSize = ciphertext.size.toLong(),
                        sha256Hash = sha256Hex(ciphertext),
                        opaqueMetadata = metadataBytes,
                        reservedForwardSecrecyMetadata = byteArrayOf(),
                        lineageId = configuration.lineageId,
                        formatVersion = BackupArchive.FORMAT_VERSION,
                    ),
                )

            httpUpload(create.uploadUrl, ciphertext)
            val committed =
                backupServiceClient.commitBackup(
                    CommitBackupRequest(
                        backupId = create.backupId,
                        byteSize = ciphertext.size.toLong(),
                        sha256Hash = sha256Hex(ciphertext),
                    ),
                )
            val backupAt = committed.backup?.committedAt?.takeIf { it.isNotBlank() } ?: committed.backup?.createdAt
            val backupAtMillis = backupAt?.let(::parseInstantMillis) ?: System.currentTimeMillis()

            recoveryKeyManager.updateBackupState(
                timestampMillis = backupAtMillis,
                contentHash = contentHash,
            )
            return BackupUploadOutcome(
                backupAtMillis = backupAtMillis,
                contentHash = contentHash,
            )
        }

        private suspend fun exportSnapshot(): BackupArchiveSnapshot {
            val contacts =
                contactDao.getAllContacts().map { contact ->
                    BackupArchiveContactFrame(
                        id = contact.id,
                        userId = contact.userId,
                        phoneNumber = contact.phoneNumber,
                        displayName = contact.displayName,
                        avatarURL = contact.avatarUrl,
                        isRegistered = contact.isRegistered,
                        isBlocked = contact.isBlocked,
                        isFavorite = contact.isFavorite,
                        lastSyncedAtMs = contact.lastSyncedAt,
                    )
                }

            val conversations =
                conversationDao.getAllConversations().map { conversation ->
                    BackupArchiveConversationFrame(
                        id = conversation.id,
                        conversationType = conversation.type,
                        title = conversation.title,
                        avatarURL = conversation.avatarUrl,
                        participantIDs = parseParticipantIds(conversation.participantIds),
                        lastMessageID = conversation.lastMessageId,
                        lastMessagePreview = conversation.lastMessagePreview,
                        lastMessageTimestampMs = conversation.lastMessageTimestamp,
                        lastMessageSenderID = null,
                        lastMessageStatus = null,
                        lastMessageContentType = null,
                        lastMessageContentBody = null,
                        unreadCount = conversation.unreadCount,
                        isPinned = conversation.isPinned,
                        isMuted = conversation.isMuted,
                        isArchived = conversation.isArchived,
                        disappearingDurationMs = conversation.disappearingDurationMs,
                        createdAtMs = conversation.createdAt,
                        updatedAtMs = conversation.updatedAt,
                    )
                }

            val currentUserId = sessionManager.getUserId().orEmpty()
            val messages =
                messageDao.getAllMessages().map { message ->
                    BackupArchiveMessageFrame(
                        id = message.id,
                        conversationID = message.conversationId,
                        senderID = message.senderId,
                        timestampMs = message.timestamp,
                        contentType = message.contentType,
                        contentBody = message.contentBody,
                        previewText = previewTextFor(message.contentType, message.contentBody),
                        status = message.status,
                        isOutgoing = message.senderId == currentUserId,
                        replyToMessageID = message.replyToId,
                        expiresAtMs = message.expiresAt,
                        isDeleted = message.isDeleted,
                    )
                }

            val info =
                BackupArchiveInfoFrame(
                    formatVersion = BackupArchive.FORMAT_VERSION,
                    exportedAtMs = System.currentTimeMillis(),
                    platform = "android",
                    appVersion = appVersion(),
                    contactCount = contacts.size,
                    conversationCount = conversations.size,
                    messageCount = messages.size,
                    vaultItemCount = 0,
                )

            return BackupArchiveSnapshot(
                info = info,
                contacts = contacts,
                conversations = conversations,
                messages = messages,
                vaultItems = emptyList(),
            )
        }

        private suspend fun restoreSnapshot(snapshot: BackupArchiveSnapshot) {
            database.withTransaction {
                pendingMessageAckDao.deleteAll()
                messageDao.deleteAllMessages()
                conversationDao.deleteAllConversations()
                contactDao.deleteAllContacts()

                val restoredContacts =
                    snapshot.contacts.map { contact ->
                        ContactEntity(
                            id = contact.id,
                            userId = contact.userId,
                            phoneNumber = contact.phoneNumber,
                            displayName = contact.displayName,
                            avatarUrl = contact.avatarURL,
                            isRegistered = contact.isRegistered || contact.isVerified,
                            isBlocked = contact.isBlocked,
                            isFavorite = contact.isFavorite,
                            lastSyncedAt = contact.lastSyncedAtMs,
                        )
                    }
                if (restoredContacts.isNotEmpty()) {
                    contactDao.insertContacts(restoredContacts)
                }

                snapshot.conversations.forEach { conversation ->
                    conversationDao.insertConversation(
                        ConversationEntity(
                            id = conversation.id,
                            type = conversation.conversationType,
                            title = conversation.title,
                            avatarUrl = conversation.avatarURL,
                            participantIds =
                                BackupArchive.json.encodeToString(
                                    kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()),
                                    conversation.participantIDs,
                                ),
                            lastMessageId = conversation.lastMessageID,
                            lastMessagePreview = conversation.lastMessagePreview,
                            lastMessageTimestamp = conversation.lastMessageTimestampMs,
                            unreadCount = conversation.unreadCount,
                            isPinned = conversation.isPinned,
                            isMuted = conversation.isMuted,
                            isArchived = conversation.isArchived,
                            disappearingDurationMs = conversation.disappearingDurationMs,
                            updatedAt = conversation.updatedAtMs,
                            createdAt = conversation.createdAtMs,
                        ),
                    )
                }

                snapshot.messages.forEach { message ->
                    messageDao.insertMessage(
                        MessageEntity(
                            id = message.id,
                            conversationId = message.conversationID,
                            senderId = message.senderID,
                            contentType = message.contentType,
                            contentBody = message.contentBody,
                            status = message.status,
                            timestamp = message.timestampMs,
                            editedAt = null,
                            replyToId = message.replyToMessageID,
                            expiresAt = message.expiresAtMs,
                            isDeleted = message.isDeleted,
                        ),
                    )
                }
            }
        }

        private fun deriveMaterial(recoveryKey: String): DerivedBackupMaterial {
            val userId = sessionManager.getUserId()
            require(!userId.isNullOrBlank()) { "User ID is unavailable" }
            return backupKeyDeriver.deriveMaterial(recoveryKey, userId)
        }

        private fun parseParticipantIds(raw: String): List<String> =
            runCatching {
                BackupArchive.json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()),
                    raw,
                )
            }.getOrElse {
                raw.split(',').map(String::trim).filter(String::isNotBlank)
            }

        private fun previewTextFor(
            contentType: String,
            contentBody: String,
        ): String =
            when (contentType.lowercase()) {
                "text" -> contentBody
                "image" ->
                    runCatching {
                        BackupArchive.json.decodeFromString(BackupArchiveMediaPayload.serializer(), contentBody).caption
                    }.getOrNull() ?: "[Image]"
                "video" -> "[Video]"
                "audio", "voice" -> "[Voice message]"
                "document", "file" ->
                    runCatching {
                        BackupArchive.json.decodeFromString(BackupArchiveMediaPayload.serializer(), contentBody).fileName
                    }.getOrNull() ?: "[File]"
                "location" ->
                    runCatching {
                        BackupArchive.json.decodeFromString(BackupArchiveLocationPayload.serializer(), contentBody).label
                    }.getOrNull() ?: "[Location]"
                "contact" ->
                    runCatching {
                        BackupArchive.json.decodeFromString(BackupArchiveContactPayload.serializer(), contentBody).name
                    }.getOrNull() ?: "[Contact]"
                else -> contentBody
            }

        private fun selectLatestBackup(
            backups: List<BackupMetadata>,
            preferredLineageId: String?,
        ): BackupMetadata? {
            val filtered =
                backups.filter { backup ->
                    preferredLineageId.isNullOrBlank() || backup.lineageId == preferredLineageId
                }
            val candidates = if (filtered.isNotEmpty()) filtered else backups
            return candidates.maxByOrNull { parseInstantMillis(it.committedAt.ifBlank { it.createdAt }) ?: 0L }
        }

        private fun parseInstantMillis(value: String): Long? =
            runCatching {
                Instant.parse(value).toEpochMilli()
            }.getOrNull()

        private fun appVersion(): String =
            runCatching {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                info.versionName ?: "unknown"
            }.getOrElse { "unknown" }

        private fun httpUpload(
            url: String,
            bytes: ByteArray,
        ) {
            val connection =
                (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/octet-stream")
                }
            try {
                connection.outputStream.use { it.write(bytes) }
                val code = connection.responseCode
                check(code in 200..299) { "Backup upload failed with HTTP $code" }
            } finally {
                connection.disconnect()
            }
        }

        private fun httpDownload(url: String): ByteArray {
            val connection =
                (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                }
            return try {
                val code = connection.responseCode
                check(code in 200..299) { "Backup download failed with HTTP $code" }
                connection.inputStream.use { stream ->
                    val output = ByteArrayOutputStream()
                    stream.copyTo(output)
                    output.toByteArray()
                }
            } finally {
                connection.disconnect()
            }
        }

        private fun aesCbcEncrypt(
            plaintext: ByteArray,
            key: ByteArray,
            iv: ByteArray,
        ): ByteArray {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            return cipher.doFinal(plaintext)
        }

        private fun aesCbcDecrypt(
            ciphertext: ByteArray,
            key: ByteArray,
            iv: ByteArray,
        ): ByteArray {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            return cipher.doFinal(ciphertext)
        }

        private fun hmac(
            iv: ByteArray,
            ciphertext: ByteArray,
            key: ByteArray,
        ): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            mac.update(iv)
            mac.update(ciphertext)
            return mac.doFinal()
        }

        private fun sha256Hex(bytes: ByteArray): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(bytes)
                .joinToString(separator = "") { "%02x".format(it) }
    }
