package com.sanchr.core.network.link

import java.net.IDN
import java.net.InetAddress
import java.net.URI

object LinkDetector {
    private val URL = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"']+)""")
    private const val TRAILING = ".,;:!?)]}\"'"
    private const val MIN_HOST_LENGTH = 4

    /**
     * The first `http(s)://` or `www.` link in [text] as an absolute URL, or
     * null.
     *
     * A link that passes here gets fetched for a preview, so the host is
     * parsed rather than split on punctuation. Taking everything before the
     * first slash includes the userinfo, so `https://x.y@127.0.0.1/admin`
     * presented a host of "x.y@127.0.0.1" — which contains a dot and so
     * passed — and the preview fetcher then made the recipient's phone request
     * its own loopback address on the sender's behalf. A message is not
     * permitted to choose what the device connects to.
     */
    fun firstUrl(text: String): String? {
        val raw =
            URL
                .find(text)
                ?.groupValues
                ?.get(1)
                ?.trimEnd { it in TRAILING } ?: return null
        if (raw.isEmpty()) return null
        val absolute = if (raw.startsWith("www.", ignoreCase = true)) "https://$raw" else raw
        val host = hostOf(absolute) ?: return null
        // Needs a host with a dot, otherwise "http://localhost"-style strings
        // and typos become cards.
        if (!host.contains('.') || host.length <= MIN_HOST_LENGTH) return null
        if (isPrivateHost(host)) return null
        return absolute
    }

    /**
     * The parsed host, lowercased and punycoded, or null when there is none.
     *
     * `URI.host` is tried first and returns null for a non-ASCII host —
     * `https://bücher.example` among them — so there is a fallback that takes
     * the authority apart by hand. It removes the userinfo at the last `@`,
     * which is the separator (a `@` inside userinfo has to be
     * percent-encoded), rather than keeping everything before the first slash
     * as the old check did.
     */
    fun hostOf(url: String): String? {
        val parsed = runCatching { URI(url) }.getOrNull()
        val host =
            parsed?.host?.takeIf { it.isNotBlank() }
                ?: authorityOf(parsed, url)?.substringAfterLast('@')?.let(::withoutPort)?.takeIf { it.isNotBlank() }
                ?: return null
        return runCatching { IDN.toASCII(host) }.getOrDefault(host).lowercase()
    }

    private fun authorityOf(
        parsed: URI?,
        url: String,
    ): String? =
        parsed?.rawAuthority?.takeIf { it.isNotBlank() }
            ?: url
                .substringAfter("://", "")
                .takeIf { it.isNotEmpty() }
                ?.substringBefore('/')
                ?.substringBefore('?')
                ?.substringBefore('#')

    /** Strips `:port`, leaving a bracketed IPv6 literal intact. */
    private fun withoutPort(host: String): String =
        when {
            host.startsWith("[") -> host.substringBefore(']').removePrefix("[")
            host.count { it == ':' } == 1 -> host.substringBefore(':')
            else -> host
        }

    /**
     * Whether [host] names the device itself or something on its network.
     *
     * Literal addresses only: resolving a name here would mean a DNS lookup
     * for every link in every message, and a name that resolves to a private
     * address can still get through — stopping that needs a check at the
     * socket, not at the text. What this does close is the direct case, where
     * a message simply names the address it wants fetched.
     */
    fun isPrivateHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost")) return true
        // `.local` is mDNS; `.internal` and `.home.arpa` are reserved for
        // private networks.
        if (host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".home.arpa")) return true
        if (!looksLikeIpLiteral(host)) return false
        val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return true
        return address.isLoopbackAddress ||
            address.isAnyLocalAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
    }

    /**
     * Whether [host] is an address rather than a name.
     *
     * Checked before [InetAddress.getByName], which would otherwise resolve a
     * name over the network — the thing this is trying not to do.
     */
    private fun looksLikeIpLiteral(host: String): Boolean =
        host.all { it.isDigit() || it == '.' } || host.contains(':') || host.startsWith("[")
}
