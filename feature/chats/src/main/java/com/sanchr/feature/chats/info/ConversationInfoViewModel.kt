package com.sanchr.feature.chats.info

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.datastore.UserPreferences
import com.sanchr.core.model.ChatVaultPolicy
import com.sanchr.core.model.Conversation
import com.sanchr.core.model.ConversationType
import com.sanchr.domain.contacts.ContactRepository
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
    /** Whether this chat's media may be auto-saved to the gallery. Visible by default, as on iOS. */
    val mediaVisibleInGallery: Boolean = true,
    /** Whether the other person in a one-to-one chat is blocked. */
    val isBlocked: Boolean = false,
) {
    val title: String get() = conversation?.title ?: "Chat"

    /**
     * The other person, in a one-to-one chat; null in a group.
     *
     * The screen worked this out for itself as "the first participant who is
     * not me", which in a group is just whoever comes first — so a group's
     * info page offered to compare security codes with one arbitrary member.
     */
    val peerId: String?
        get() =
            conversation
                ?.takeIf { it.type == ConversationType.DIRECT }
                ?.participants
                ?.firstOrNull { it.displayName != SELF_DISPLAY_NAME }
                ?.id
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
        private val userPreferences: UserPreferences,
        savedStateHandle: SavedStateHandle,
        private val contactRepository: ContactRepository,
    ) : ViewModel() {
        private val conversationId: String = savedStateHandle["conversationId"] ?: ""

        private val _uiState = MutableStateFlow(ConversationInfoUiState())
        val uiState: StateFlow<ConversationInfoUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                messageRepository
                    .observeConversation(conversationId)
                    .catch { /* DB closed during logout */ }
                    .collect { conversation ->
                        _uiState.update { it.copy(conversation = conversation) }
                        loadBlockedOnce()
                    }
            }
            viewModelScope.launch {
                userPreferences
                    .mediaVisibleInGallery(conversationId)
                    .collect { visible -> _uiState.update { it.copy(mediaVisibleInGallery = visible) } }
            }
        }

        fun setMuted(muted: Boolean) = action { messageRepository.setMuted(conversationId, muted) }

        fun setArchived(archived: Boolean) = action { messageRepository.setArchived(conversationId, archived) }

        fun hide() = action { messageRepository.setHidden(conversationId, true) }

        /**
         * Whether media from this chat may reach the system gallery through
         * Media Auto-Save, as iOS's Media Visibility switch does. Stored per
         * conversation and per device: it says where this phone's copies may
         * go, which is not something to share with the account.
         */
        fun setMediaVisibleInGallery(visible: Boolean) =
            action {
                userPreferences.setMediaVisibleInGallery(conversationId, visible)
            }

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

        /**
         * Changes this chat's vault policy.
         *
         * Takes a change rather than a whole policy so that two switches
         * flipped in quick succession both land: each is applied to what is
         * stored at that moment, not to what the screen last drew. Does
         * nothing before the conversation has loaded, when there is no stored
         * policy to change yet.
         */
        fun updateVaultPolicy(change: (ChatVaultPolicy) -> ChatVaultPolicy) {
            val current = _uiState.value.conversation?.vaultPolicy ?: return
            val next = change(current)
            if (next == current) return
            // Shown at once; the stored row follows and the observer confirms it.
            _uiState.update { state -> state.copy(conversation = state.conversation?.copy(vaultPolicy = next)) }
            action { messageRepository.setVaultPolicy(conversationId, next) }
        }

        fun dismissError() = _uiState.update { it.copy(error = null) }

        private var blockedLoadedFor: String? = null

        /** Asked once per peer, when the conversation first says who that is. */
        private suspend fun loadBlockedOnce() {
            val peerId = _uiState.value.peerId ?: return
            if (peerId == blockedLoadedFor) return
            blockedLoadedFor = peerId
            val blocked = runCatching { contactRepository.isBlocked(peerId) }.getOrDefault(false)
            _uiState.update { it.copy(isBlocked = blocked) }
        }

        /**
         * Blocks or unblocks the other person in this chat.
         *
         * Blocking existed, on the contact's profile and in the contact list,
         * and nowhere in the chat itself — which is where someone is when
         * they decide they want it. iOS has it on this page.
         */
        fun setBlocked(blocked: Boolean) {
            val peerId = _uiState.value.peerId ?: return
            action {
                contactRepository.setBlocked(peerId, blocked)
                _uiState.update { it.copy(isBlocked = blocked) }
            }
        }

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

/** How the conversation mapper names the local user among a chat's participants. */
private const val SELF_DISPLAY_NAME = "You"
