package com.sanchr.feature.chats.info

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.model.Conversation
import com.sanchr.domain.messaging.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the conversation's own settings page shows. */
data class ConversationInfoUiState(
    val conversation: Conversation? = null,
    val error: String? = null,
) {
    val title: String get() = conversation?.title ?: "Chat"
    val isMuted: Boolean get() = conversation?.isMuted == true
    val isArchived: Boolean get() = conversation?.isArchived == true

    /** The per-chat timer as one of [DisappearingDurations]' labels. */
    val disappearingLabel: String
        get() = DisappearingDurations.labelOf(conversation?.disappearingMessageDuration)
}

/**
 * One conversation's settings: mute, archive, hide, and how long its messages
 * live.
 *
 * Android scattered these across the chat list's long-press menu and the
 * contact's profile, with no page for the conversation itself. iOS has had
 * `ConversationInfoView` throughout.
 */
@HiltViewModel
class ConversationInfoViewModel
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val conversationId: String = savedStateHandle["conversationId"] ?: ""

        private val _uiState = MutableStateFlow(ConversationInfoUiState())
        val uiState: StateFlow<ConversationInfoUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                messageRepository
                    .observeConversation(conversationId)
                    .catch { /* DB closed during logout */ }
                    .collect { conversation -> _uiState.update { it.copy(conversation = conversation) } }
            }
        }

        fun setMuted(muted: Boolean) = action { messageRepository.setMuted(conversationId, muted) }

        fun setArchived(archived: Boolean) = action { messageRepository.setArchived(conversationId, archived) }

        fun hide() = action { messageRepository.setHidden(conversationId, true) }

        /**
         * Sets this chat's own timer. "Off" clears it rather than storing
         * zero, so the conversation falls back to the account-wide default
         * instead of pinning itself to "never expires".
         */
        fun setDisappearing(label: String) =
            action {
                messageRepository.setDisappearingDuration(conversationId, DisappearingDurations.millisOf(label))
            }

        /** Sets this chat's wallpaper; "default" clears it so the account-wide choice applies. */
        fun setWallpaper(name: String) =
            action {
                messageRepository.setWallpaper(conversationId, name.takeIf { it != "default" })
            }

        fun dismissError() = _uiState.update { it.copy(error = null) }

        private fun action(block: suspend () -> Unit) {
            viewModelScope.launch {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = e.message ?: "Something went wrong") }
                }
            }
        }
    }
