package com.sanchr.core.network.link

/** Finds the first web link in a message, the way iOS's data detector does for `LinkPreviewCard`. */
object LinkDetector {
    private val URL = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"']+)""")
    private const val TRAILING = ".,;:!?)]}\"'"

    /** The first `http(s)://` or `www.` link in [text] as an absolute URL, or null. */
    fun firstUrl(text: String): String? {
        val raw =
            URL
                .find(text)
                ?.groupValues
                ?.get(1)
                ?.trimEnd { it in TRAILING } ?: return null
        if (raw.isEmpty()) return null
        val absolute = if (raw.startsWith("www.", ignoreCase = true)) "https://$raw" else raw
        // Needs a host with a dot, otherwise "http://localhost"-style strings and typos become cards.
        val host = absolute.substringAfter("://").substringBefore('/').substringBefore('?')
        return absolute.takeIf { host.contains('.') && host.length > 3 }
    }
}
