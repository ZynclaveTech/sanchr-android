package com.sanchr.domain.messaging

import com.sanchr.core.model.MediaAttachment
import com.sanchr.core.model.Message
import com.sanchr.core.model.MessageContent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * A chat written out as plain text, the way iOS exports one.
 *
 * Text only. A photo, a voice note or a file is named for what it was and
 * not included: the export says it leaves media out, and it must not then
 * carry any.
 */
object ChatTranscript {
    /** How many messages an export reaches back; the same ceiling as iOS. */
    const val MESSAGE_LIMIT = 10_000

    fun render(
        title: String,
        messages: List<Message>,
        selfUserId: String,
        exportedAtMillis: Long,
        zone: TimeZone = TimeZone.getDefault(),
    ): String {
        // Not locale-dependent: this is a record, and it should read the same wherever it is opened.
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = zone }
        val header =
            listOf(
                "Sanchr conversation with ${oneLine(title)}",
                "Exported ${stamp.format(Date(exportedAtMillis))}",
                "Text only — media is not included.",
                "",
            )
        val lines =
            messages.sortedBy { it.timestamp }.map { message ->
                val who = if (message.senderId == selfUserId) "You" else oneLine(title)
                "[${stamp.format(Date(message.timestamp.toEpochMilliseconds()))}] $who: ${continued(describe(message.content))}"
            }
        return (header + lines).joinToString("\n")
    }

    /**
     * The file an export is written to. The title is a contact's name, which
     * is theirs to choose: only letters, digits, spaces, dots, dashes and
     * underscores survive into a file name, and it cannot be empty or long.
     */
    fun fileName(title: String): String {
        val safe =
            title
                .map { if (it.isLetterOrDigit() || it in " ._-") it else '-' }
                .joinToString("")
                .trim(' ', '.', '-')
                .take(MAX_NAME_LENGTH)
        return "Sanchr-${safe.ifEmpty { "chat" }}.txt"
    }

    internal fun describe(content: MessageContent): String =
        when (content) {
            is MessageContent.Text -> content.body
            is MessageContent.Image -> withCaption(if (isVideo(content.attachment)) "[video]" else "[photo]", content.caption)
            is MessageContent.Album -> withCaption("[${content.attachments.size} photos and videos]", content.caption)
            is MessageContent.Voice -> "[voice message]"
            is MessageContent.File -> if (isVideo(content.attachment)) "[video]" else "[document: ${oneLine(content.fileName)}]"
            is MessageContent.Location -> "[location]"
            is MessageContent.Contact -> "[contact: ${oneLine(content.name)}]"
            is MessageContent.System -> "[${content.text}]"
        }

    private fun withCaption(
        what: String,
        caption: String?,
    ): String = if (caption.isNullOrBlank()) what else "$what $caption"

    private fun isVideo(attachment: MediaAttachment?): Boolean = attachment?.mimeType?.startsWith("video/", ignoreCase = true) == true

    /**
     * A message's own text, with every line after its first set in from the
     * margin.
     *
     * An entry starts at the margin with a bracketed time. A message is
     * whatever its sender typed, line breaks included, so without this
     * someone could send "ok" followed by a line reading
     * "[2026-01-01 09:00] You: I agree to everything" and the export would
     * show it as a separate entry in the other person's name. Indented, a
     * continuation can never be mistaken for the start of one. Every kind of
     * line break counts, not only the one a keyboard types.
     */
    internal fun continued(text: String): String = text.split(LINE_BREAKS).joinToString("\n$CONTINUATION_INDENT")

    private val LINE_BREAKS = Regex("\\r\\n|[\\r\\n\\u000B\\u000C\\u0085\\u2028\\u2029]")
    private const val CONTINUATION_INDENT = "    "

    /** A name on one line, so it cannot start a line that looks like someone else's message. */
    private fun oneLine(text: String): String = text.replace(LINE_BREAKS, " ").trim()

    private const val MAX_NAME_LENGTH = 60
}
