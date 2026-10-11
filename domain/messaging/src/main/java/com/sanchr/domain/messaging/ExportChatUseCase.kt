package com.sanchr.domain.messaging

import android.content.Context
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.datastore.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Writes a chat's text to a file for the user to send somewhere.
 *
 * There was no way to get a conversation out of the app as words. iOS has
 * Export Chat; this is the same thing in the same format.
 *
 * The file is the chat in the clear, so it is kept in the app's private
 * cache — where the share sheet can lend it out and nothing else can read it
 * — and any earlier export is removed first: at most one exists at a time,
 * and clearing the cache or removing the account takes it too.
 */
class ExportChatUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val messageRepository: MessageRepository,
        private val sessionManager: SessionManager,
        private val dispatchers: DispatcherProvider,
    ) {
        /** @return the file, or null when the chat has no messages to export. */
        suspend operator fun invoke(
            conversationId: String,
            title: String,
        ): File? =
            withContext(dispatchers.io) {
                val messages = messageRepository.observeRecentMessages(conversationId, ChatTranscript.MESSAGE_LIMIT).first()
                if (messages.isEmpty()) return@withContext null
                val directory = File(context.cacheDir, DIRECTORY)
                directory.deleteRecursively()
                directory.mkdirs()
                File(directory, ChatTranscript.fileName(title)).apply {
                    writeText(
                        ChatTranscript.render(
                            title = title,
                            messages = messages,
                            selfUserId = sessionManager.getUserId().orEmpty(),
                            exportedAtMillis = System.currentTimeMillis(),
                        ),
                    )
                }
            }

        companion object {
            /** Under the cache, and named in the app's file-provider paths. */
            const val DIRECTORY = "exports"
        }
    }
