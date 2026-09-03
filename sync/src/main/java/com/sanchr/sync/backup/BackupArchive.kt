package com.sanchr.sync.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object BackupArchive {
    const val FORMAT_VERSION: Int = 1
    const val AUTOMATIC_BACKUP_INTERVAL_MS: Long = 6 * 60 * 60 * 1000L

    val json: Json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
}

enum class BackupFrameType {
    @SerialName("info")
    INFO,

    @SerialName("contact")
    CONTACT,

    @SerialName("conversation")
    CONVERSATION,

    @SerialName("message")
    MESSAGE,

    @SerialName("vault_item")
    VAULT_ITEM,
}

@Serializable
data class BackupArchiveRecordCounts(
    val contacts: Int,
    val conversations: Int,
    val messages: Int,
    val vaultItems: Int,
)

data class BackupArchiveSnapshot(
    val info: BackupArchiveInfoFrame,
    val contacts: List<BackupArchiveContactFrame>,
    val conversations: List<BackupArchiveConversationFrame>,
    val messages: List<BackupArchiveMessageFrame>,
    val vaultItems: List<BackupArchiveVaultItemFrame>,
) {
    val counts: BackupArchiveRecordCounts
        get() =
            BackupArchiveRecordCounts(
                contacts = contacts.size,
                conversations = conversations.size,
                messages = messages.size,
                vaultItems = vaultItems.size,
            )
}

@Serializable
private data class BackupFrameProbe(
    val type: String,
)

@Serializable
data class BackupArchiveInfoFrame(
    val type: String = "info",
    val formatVersion: Int,
    val exportedAtMs: Long,
    val platform: String,
    val appVersion: String,
    val contactCount: Int,
    val conversationCount: Int,
    val messageCount: Int,
    val vaultItemCount: Int,
)

@Serializable
data class BackupArchiveMediaPayload(
    val url: String,
    val thumbnailURL: String? = null,
    val encryptionKeyBase64: String? = null,
    val encryptionIVBase64: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val caption: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val fileName: String? = null,
)

@Serializable
data class BackupArchiveLocationPayload(
    val latitude: Double,
    val longitude: Double,
    val label: String? = null,
)

@Serializable
data class BackupArchiveContactPayload(
    val name: String,
    val phoneNumber: String,
)

@Serializable
data class BackupArchiveContactFrame(
    val type: String = "contact",
    val id: String,
    val userId: String? = null,
    val phoneNumber: String,
    val displayName: String,
    val avatarURL: String? = null,
    val bio: String? = null,
    val isVerified: Boolean = false,
    val lastSeenMs: Long? = null,
    val status: String = "offline",
    val isLocalUser: Boolean = false,
    val isRegistered: Boolean = false,
    val isBlocked: Boolean = false,
    val isFavorite: Boolean = false,
    val lastSyncedAtMs: Long? = null,
)

@Serializable
data class BackupArchiveConversationFrame(
    val type: String = "conversation",
    val id: String,
    val conversationType: String,
    val title: String? = null,
    val avatarURL: String? = null,
    val participantIDs: List<String>,
    val lastMessageID: String? = null,
    val lastMessagePreview: String? = null,
    val lastMessageTimestampMs: Long? = null,
    val lastMessageSenderID: String? = null,
    val lastMessageStatus: String? = null,
    val lastMessageContentType: String? = null,
    val lastMessageContentBody: String? = null,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isArchived: Boolean = false,
    val disappearingDurationMs: Long? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

@Serializable
data class BackupArchiveMessageFrame(
    val type: String = "message",
    val id: String,
    val conversationID: String,
    val senderID: String,
    val timestampMs: Long,
    val contentType: String,
    val contentBody: String,
    val previewText: String? = null,
    val status: String,
    val isOutgoing: Boolean = false,
    val replyToMessageID: String? = null,
    val expiresAtMs: Long? = null,
    val isDeleted: Boolean = false,
)

@Serializable
data class BackupArchiveVaultItemFrame(
    val type: String = "vault_item",
    val id: String,
    val name: String,
    val itemType: String,
    val sizeBytes: Long,
    val encryptionKeyBase64: String,
    val encryptionIVBase64: String,
    val encryptedThumbnailURL: String? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val isCachedLocally: Boolean = false,
    val remoteURL: String? = null,
    val localURL: String? = null,
)

object BackupArchiveSerializer {
    fun serialize(snapshot: BackupArchiveSnapshot): ByteArray {
        val lines =
            buildList {
                add(BackupArchive.json.encodeToString(BackupArchiveInfoFrame.serializer(), snapshot.info))
                snapshot.contacts.forEach {
                    add(BackupArchive.json.encodeToString(BackupArchiveContactFrame.serializer(), it))
                }
                snapshot.conversations.forEach {
                    add(BackupArchive.json.encodeToString(BackupArchiveConversationFrame.serializer(), it))
                }
                snapshot.messages.forEach {
                    add(BackupArchive.json.encodeToString(BackupArchiveMessageFrame.serializer(), it))
                }
                snapshot.vaultItems.forEach {
                    add(BackupArchive.json.encodeToString(BackupArchiveVaultItemFrame.serializer(), it))
                }
            }
        return (lines.joinToString("\n", postfix = "\n")).encodeToByteArray()
    }

    fun deserialize(data: ByteArray): BackupArchiveSnapshot {
        val lines =
            data
                .decodeToString()
                .lineSequence()
                .filter { it.isNotBlank() }
                .toList()

        var info: BackupArchiveInfoFrame? = null
        val contacts = mutableListOf<BackupArchiveContactFrame>()
        val conversations = mutableListOf<BackupArchiveConversationFrame>()
        val messages = mutableListOf<BackupArchiveMessageFrame>()
        val vaultItems = mutableListOf<BackupArchiveVaultItemFrame>()

        lines.forEach { line ->
            val probe = BackupArchive.json.decodeFromString(BackupFrameProbe.serializer(), line)
            when (probe.type) {
                "info" -> info = BackupArchive.json.decodeFromString(BackupArchiveInfoFrame.serializer(), line)
                "contact" ->
                    contacts +=
                        BackupArchive.json.decodeFromString(
                            BackupArchiveContactFrame.serializer(),
                            line,
                        )
                "conversation" ->
                    conversations +=
                        BackupArchive.json.decodeFromString(
                            BackupArchiveConversationFrame.serializer(),
                            line,
                        )
                "message" ->
                    messages +=
                        BackupArchive.json.decodeFromString(
                            BackupArchiveMessageFrame.serializer(),
                            line,
                        )
                "vault_item" ->
                    vaultItems +=
                        BackupArchive.json.decodeFromString(
                            BackupArchiveVaultItemFrame.serializer(),
                            line,
                        )
            }
        }

        val snapshotInfo = requireNotNull(info) { "Backup archive is missing its info frame" }
        return BackupArchiveSnapshot(
            info = snapshotInfo,
            contacts = contacts,
            conversations = conversations,
            messages = messages,
            vaultItems = vaultItems,
        )
    }
}
