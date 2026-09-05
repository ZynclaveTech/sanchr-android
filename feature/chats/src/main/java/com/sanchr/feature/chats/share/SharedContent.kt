package com.sanchr.feature.chats.share

/**
 * What another app handed us through the system share sheet.
 *
 * Modelled after iOS `SharePayload`. Text and attachments are separate
 * cases rather than one struct with optional fields, so the send path
 * cannot accidentally ship an empty message alongside a photo.
 */
sealed interface SharedContent {
    /** Shared text, which includes shared links: Android sends both as `text/plain`. */
    data class Text(
        val body: String,
    ) : SharedContent

    /** One or more files, each still an opaque `content://` string at this point. */
    data class Attachments(
        val uris: List<String>,
        /** Text shared alongside the files, offered as the caption. */
        val caption: String? = null,
    ) : SharedContent
}

/**
 * Turns the pieces of a share intent into content we can send.
 *
 * Kept free of Android types so the classification is testable on its own:
 * deciding what was shared is the part that goes wrong, and it should not
 * need an emulator to check.
 */
object SharedContentParser {
    /**
     * @param uris the `EXTRA_STREAM` values, already flattened to strings.
     * @param text the `EXTRA_TEXT` value, if any.
     * @param subject the `EXTRA_SUBJECT` value, used only when there is no text.
     *
     * Returns null when the share carried nothing we can send, so the caller
     * shows a message rather than opening an empty composer.
     */
    fun parse(
        uris: List<String>,
        text: String?,
        subject: String? = null,
    ): SharedContent? {
        val cleanUris = uris.filter { it.isNotBlank() }
        val body = text?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanUris.isNotEmpty()) {
            // Text alongside files is a caption, not a second message: sharing
            // a photo from a gallery often carries its filename or a blurb, and
            // sending that as its own bubble reads as a duplicate.
            return SharedContent.Attachments(uris = cleanUris, caption = body)
        }
        // Subject is the fallback only: many apps set both, and the subject is
        // usually a truncated restatement of the text.
        val fallback = body ?: subject?.trim()?.takeIf { it.isNotEmpty() }
        return fallback?.let(SharedContent::Text)
    }
}
