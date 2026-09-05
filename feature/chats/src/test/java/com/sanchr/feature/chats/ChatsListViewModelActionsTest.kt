package com.sanchr.feature.chats

import androidx.work.WorkManager
import com.sanchr.core.model.Conversation
import com.sanchr.core.model.ConversationType
import com.sanchr.domain.contacts.ContactRepository
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.ObserveConversationsUseCase
import com.sanchr.sync.SyncState
import com.sanchr.sync.realtime.RealtimeManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant

/** Row actions persist through the repository and surface failures as a one-shot error. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatsListViewModelActionsTest {
    private val testDispatcher = StandardTestDispatcher()
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val observeConversationsUseCase = mockk<ObserveConversationsUseCase> { every { this@mockk.invoke() } returns emptyFlow() }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { messageRepository.observeArchivedConversations() } returns flowOf(emptyList())
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() =
        ChatsListViewModel(
            observeConversationsUseCase = observeConversationsUseCase,
            workManager = mockk<WorkManager>(relaxed = true),
            contactRepository = mockk<ContactRepository>(relaxed = true),
            messageRepository = messageRepository,
            syncState = mockk<SyncState>(relaxed = true) { every { isSyncing } returns MutableStateFlow(false) },
            sessionManager = mockk(relaxed = true),
            realtimeManager = mockk<RealtimeManager>(relaxed = true) { every { typingCache } returns MutableStateFlow(emptyMap()) },
        )

    private fun conversation(
        pinned: Boolean = false,
        muted: Boolean = false,
    ) = Conversation(
        id = "c1",
        type = ConversationType.DIRECT,
        participants = emptyList(),
        isPinned = pinned,
        isMuted = muted,
        updatedAt = Instant.fromEpochMilliseconds(1),
        createdAt = Instant.fromEpochMilliseconds(1),
    )

    @Test
    fun `pin, mute, archive, mark read and delete go through the repository with the flipped value`() =
        runTest(testDispatcher) {
            val vm = vm()
            vm.togglePin(conversation(pinned = true))
            vm.toggleMute(conversation(muted = false))
            vm.setArchived(conversation(), archived = true)
            vm.markAsRead(conversation())
            vm.deleteConversation(conversation())
            advanceUntilIdle()

            coVerify { messageRepository.setPinned("c1", false) }
            coVerify { messageRepository.setMuted("c1", true) }
            coVerify { messageRepository.setArchived("c1", true) }
            coVerify { messageRepository.markAsRead("c1") }
            coVerify { messageRepository.deleteConversation("c1") }
        }

    @Test
    fun `a failing action becomes a one-shot error the screen can show`() =
        runTest(testDispatcher) {
            coEvery { messageRepository.setMuted(any(), any()) } throws IOException("offline")
            val vm = vm()
            vm.toggleMute(conversation())
            advanceUntilIdle()
            assertEquals("offline", vm.actionError.value)
            vm.dismissActionError()
            assertEquals(null, vm.actionError.value)
        }
}
