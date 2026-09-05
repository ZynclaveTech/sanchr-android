package com.sanchr.feature.chats.share

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanchr.core.model.Conversation
import com.sanchr.core.model.ConversationType
import kotlinx.datetime.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders the share flow for real. The unit tests cover what the state
 * machine decides; this covers that the screen a shared photo actually lands
 * on draws, lists chats, filters them, and moves to the review step.
 */
@RunWith(AndroidJUnit4::class)
class ShareTargetContentTest {
    @get:Rule
    val compose = createComposeRule()

    private fun conversation(
        id: String,
        title: String,
    ) = Conversation(
        id = id,
        type = ConversationType.DIRECT,
        participants = emptyList(),
        title = title,
        updatedAt = Instant.fromEpochMilliseconds(1),
        createdAt = Instant.fromEpochMilliseconds(0),
    )

    private val conversations = listOf(conversation("c1", "Ravi Kumar"), conversation("c2", "Aditi Sharma"))

    private fun render(
        state: ShareTargetUiState,
        content: SharedContent = SharedContent.Text("https://example.com"),
        onToggle: (String) -> Unit = {},
        onQueryChanged: (String) -> Unit = {},
        onNext: () -> Unit = {},
    ) {
        compose.setContent {
            ShareTargetContent(
                uiState = state,
                content = content,
                onBack = {},
                onQueryChanged = onQueryChanged,
                onToggle = onToggle,
                onNext = onNext,
                onCaptionChanged = {},
                onSend = {},
            )
        }
    }

    @Test
    fun the_picker_lists_every_chat_and_reports_a_tap() {
        var toggled: String? = null
        render(ShareTargetUiState(conversations = conversations), onToggle = { toggled = it })

        compose.onNodeWithText("Share with").assertExists()
        compose.onNodeWithText("Ravi Kumar").assertExists()
        compose.onNodeWithText("Aditi Sharma").assertExists()

        compose.onNodeWithText("Ravi Kumar").performClick()
        assert(toggled == "c1") { "tapping a row must select that conversation, got $toggled" }
    }

    @Test
    fun searching_narrows_the_visible_chats() {
        render(ShareTargetUiState(conversations = conversations, query = "aditi"))

        compose.onNodeWithText("Aditi Sharma").assertExists()
        compose.onNodeWithText("Ravi Kumar").assertDoesNotExist()
    }

    @Test
    fun the_search_box_reports_what_is_typed() {
        var typed = ""
        render(ShareTargetUiState(conversations = conversations), onQueryChanged = { typed = it })

        compose.onNodeWithText("Search chats").performTextInput("rav")

        assert(typed == "rav") { "the search box must report its input, got '$typed'" }
    }

    @Test
    fun next_is_disabled_until_a_chat_is_chosen() {
        render(ShareTargetUiState(conversations = conversations))

        compose.onNodeWithText("Next").assertIsNotEnabled()
    }

    @Test
    fun choosing_a_chat_enables_next_and_shows_the_count() {
        var advanced = false
        render(ShareTargetUiState(conversations = conversations, selectedIds = setOf("c1")), onNext = { advanced = true })

        compose.onNodeWithText("Next (1)").performClick()

        assert(advanced) { "Next must advance once a chat is chosen" }
    }

    @Test
    fun the_review_step_names_the_recipients_and_offers_a_caption_for_files() {
        render(
            ShareTargetUiState(step = ShareStep.Composing, conversations = conversations, selectedIds = setOf("c1", "c2")),
            content = SharedContent.Attachments(listOf("content://a", "content://b")),
        )

        compose.onNodeWithText("Review").assertExists()
        compose.onNodeWithText("2 files").assertExists()
        compose.onNodeWithText("Sending to 2 chats").assertExists()
        compose.onNodeWithText("Add a caption").assertExists()
        compose.onNodeWithText("Send").assertExists()
    }

    @Test
    fun shared_text_shows_no_caption_box_because_a_caption_would_be_a_second_message() {
        render(
            ShareTargetUiState(step = ShareStep.Composing, conversations = conversations, selectedIds = setOf("c1")),
            content = SharedContent.Text("https://example.com/a"),
        )

        compose.onNodeWithText("https://example.com/a").assertExists()
        compose.onNodeWithText("Sending to 1 chat").assertExists()
        compose.onNodeWithText("Add a caption").assertDoesNotExist()
    }
}
