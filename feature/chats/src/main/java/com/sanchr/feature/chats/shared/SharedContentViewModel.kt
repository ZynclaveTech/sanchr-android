package com.sanchr.feature.chats.shared

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.datastore.UserPreferences
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.media.AttachmentDownloader
import com.sanchr.feature.chats.MessageUiModel
import com.sanchr.feature.chats.info.VaultPolicyRules
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SharedContentUiState(
    val tab: SharedContent.Tab = SharedContent.Tab.MEDIA,
    val buckets: SharedContent.Buckets = SharedContent.Buckets(),
    val isLoading: Boolean = true,
    /** The account-wide setting, honoured by the viewer this screen opens. */
    val screenshotProtectionEnabled: Boolean = false,
)

/**
 * Everything one conversation has shared, for the Media / Links / Docs
 * browser.
 *
 * Reads the same windowed observer the transcript uses, so the browser covers
 * what the chat holds and grows with it rather than running a query of its
 * own against the whole table.
 */
@HiltViewModel
class SharedContentViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val messageRepository: MessageRepository,
        private val sessionManager: SessionManager,
        private val attachmentDownloader: AttachmentDownloader,
        private val userPreferences: UserPreferences,
    ) : ViewModel() {
        private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])

        private val _uiState = MutableStateFlow(SharedContentUiState())
        val uiState: StateFlow<SharedContentUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                // The same rule as the chat itself: this browser opens the
                // same media, so it must not be the unprotected way in.
                combine(
                    userPreferences.screenshotProtectionEnabled,
                    messageRepository.observeConversation(conversationId),
                ) { accountWide, conversation ->
                    VaultPolicyRules.captureProtected(accountWide, conversation?.vaultPolicy)
                }.catch { /* DB closed during logout */ }
                    .collect { on -> _uiState.update { it.copy(screenshotProtectionEnabled = on) } }
            }
            viewModelScope.launch {
                messageRepository
                    .observeRecentMessages(conversationId, WINDOW)
                    .catch { /* DB closed during logout */ }
                    .collect { messages ->
                        val models: List<MessageUiModel> = messages.toSharedContentModels(sessionManager.getUserId().orEmpty())
                        _uiState.update { it.copy(buckets = SharedContent.partition(models), isLoading = false) }
                    }
            }
        }

        fun onTabSelected(tab: SharedContent.Tab) = _uiState.update { it.copy(tab = tab) }

        /** The decrypted file for one row, downloading it if needed. */
        suspend fun openAttachment(message: MessageUiModel): File? {
            val attachment = message.attachment ?: return null
            return runCatching { attachmentDownloader.open(message.id, attachment) }.getOrNull()
        }

        /**
         * The decrypted file for a grid tile, but only if it is already on
         * disk.
         *
         * Never downloads: a grid of a conversation's whole history would
         * otherwise fetch every attachment in it the moment the screen opened,
         * which is precisely what the auto-download policy exists to prevent.
         * A tile with nothing cached shows its placeholder until it is tapped,
         * and the gallery then fetches the one that was asked for.
         */
        fun cachedThumbnail(message: MessageUiModel): File? {
            val attachment = message.attachment ?: return null
            return runCatching {
                attachmentDownloader
                    .cacheFile(message.id, attachment.mimeType)
                    .takeIf { attachmentDownloader.isCached(message.id, attachment) }
            }.getOrNull()
        }

        private companion object {
            /**
             * Deliberately wider than the transcript's opening window: someone
             * looking for a photo from weeks ago is looking past what the chat
             * shows on screen.
             */
            const val WINDOW = 2_000
        }
    }
