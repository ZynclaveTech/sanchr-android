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
     * The host, for a row that has to read at a glance. Falls back to the
     * whole link rather than to nothing when there is no host to show.
     */
    fun domainOf(url: String): String {
        val withoutScheme = url.substringAfter("://", url)
        val host = withoutScheme.substringBefore('/').substringBefore('?').removePrefix("www.")
        return host.ifBlank { url }
    }
}
