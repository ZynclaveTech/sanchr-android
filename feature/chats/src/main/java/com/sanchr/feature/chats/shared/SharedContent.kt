package com.sanchr.feature.chats.shared

import com.sanchr.core.network.link.LinkDetector
import com.sanchr.feature.chats.MessageUiModel

/**
 * Everything a conversation has shared, split into the three things people go
 * looking for: pictures, links and files.
 *
 * iOS has had `SharedContentView` throughout; Android had no way to find a
 * photo from three weeks ago except scrolling to it. The split is the same one
 * iOS makes, so the two apps group the same message the same way.
 */
object SharedContent {
    enum class Tab(
        val title: String,
    ) {
        MEDIA("Media"),
        LINKS("Links"),
        DOCS("Docs"),
    }

    /** A link found in a text message, with the message it came from. */
    data class Link(
        val messageId: String,
        val url: String,
        val displayDomain: String,
        val timestamp: Long,
    )

    data class Buckets(
        val media: List<MessageUiModel> = emptyList(),
        val links: List<Link> = emptyList(),
        val docs: List<MessageUiModel> = emptyList(),
    ) {
        fun isEmpty(tab: Tab): Boolean =
            when (tab) {
                Tab.MEDIA -> media.isEmpty()
                Tab.LINKS -> links.isEmpty()
                Tab.DOCS -> docs.isEmpty()
            }
    }

    /**
     * Sorts [messages] into the three buckets, newest first.
     *
     * A view-once photo is left out of every bucket: it exists to be seen once
     * and then wiped, so listing it in a browser would be a second way to open
     * it — and an album contributes each of its tiles, since the point of the
     * grid is to find one picture.
     */
    fun partition(messages: List<MessageUiModel>): Buckets {
        val media = ArrayList<MessageUiModel>()
        val links = ArrayList<Link>()
        val docs = ArrayList<MessageUiModel>()

        for (message in messages) {
            if (message.isViewOnce) continue
            when {
                message.isAlbum -> message.albumAttachments.indices.mapNotNullTo(media, message::albumTile)
                message.contentType == "image" && message.attachment != null -> media += message
                message.contentType == "file" && message.attachment != null -> docs += message
                message.contentType == "text" ->
                    LinkDetector.firstUrl(message.text)?.let { url ->
                        links +=
                            Link(
                                messageId = message.id,
                                url = url,
                                displayDomain = domainOf(url),
                                timestamp = message.timestamp,
                            )
                    }
            }
        }

        return Buckets(
            media = media.sortedByDescending { it.timestamp },
            links = links.sortedByDescending { it.timestamp },
            docs = docs.sortedByDescending { it.timestamp },
        )
    }

    /**
     * The host, for a row that has to read at a glance.
     *
     * Parsed rather than split on punctuation. Taking everything before the
     * first slash includes the userinfo, so
     * `https://example.com@evil.example/x` produced "example.com@evil.example"
     * — which, in a row that is one line with an ellipsis, reads as
     * "example.com@evil…", i.e. as example.com. The link is sent by whoever is
     * in the conversation, so that is a spoof someone can choose to send.
     *
     * A link whose host cannot be parsed shows in full, so a row never claims
     * a host the link does not have.
     */
    fun domainOf(url: String): String = LinkDetector.hostOf(url)?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: url
}
