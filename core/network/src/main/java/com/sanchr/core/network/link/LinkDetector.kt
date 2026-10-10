package com.sanchr.core.network.link

import java.net.InetAddress
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object LinkDetector {
    private val URL = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"']+)""")
    private const val TRAILING = ".,;:!?)]}\"'"
    private const val MIN_HOST_LENGTH = 4

    /** Every label is decimal or `0x`-hex: an address written in a non-standard form. */
    private val NUMERIC_HOST = Regex("""^(0[xX][0-9a-fA-F]+|[0-9]+)(\.(0[xX][0-9a-fA-F]+|[0-9]+))*$""")
    private val DOTTED_QUAD = Regex("""^(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])(\.(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])){3}$""")

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
     * The parsed host, lowercased and punycoded, or null when [url] is not a
     * URL this app would fetch.
     *
     * Deliberately OkHttp's own parser — the one `LinkPreviewFetcher` hands
     * the URL to when it connects. A second, hand-written parser is a second
     * opinion about what a URL means, and a request only has to be judged safe
     * by one of them and routed by the other to reach somewhere it should not.
     * Here there is one answer. A trailing dot is dropped so that
     * `127.0.0.1.` cannot evade the checks below while still resolving.
     */
    fun hostOf(url: String): String? =
        url
            .toHttpUrlOrNull()
            ?.host
            ?.removeSuffix(".")
            ?.takeIf { it.isNotBlank() }
            ?.lowercase()

    /**
     * Whether [host] names the device itself, something on its network, or an
     * address written in a form that invites disagreement about which it is.
     *
     * Names are matched by suffix only: resolving one here would mean a DNS
     * lookup for every link in every message, and a name that resolves to a
     * private address still gets through — stopping that needs a check at the
     * socket, on the address actually connected to, not at the text. What this
     * closes is the direct case, where a message simply names the address it
     * wants fetched.
     */
    fun isPrivateHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost")) return true
        // `.local` is mDNS; `.internal` and `.home.arpa` are reserved for
        // private networks.
        if (host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".home.arpa")) return true
        if (NUMERIC_HOST.matches(host)) {
            // `0x7f.1`, `2130706433` and `127.1` are all loopback to some
            // resolvers and an unremarkable string to others. Rather than pick
            // a reading, only the plain four-part form is allowed to continue.
            return !DOTTED_QUAD.matches(host) || isPrivateAddress(host)
        }
        if (host.startsWith("[") || host.contains(':')) return isPrivateAddress(host.trim('[', ']'))
        return false
    }

    /** Private when the literal cannot be read, so an unparseable address is never fetched. */
    private fun isPrivateAddress(literal: String): Boolean {
        val address = runCatching { InetAddress.getByName(literal) }.getOrNull() ?: return true
        return PrivateAddresses.isPrivate(address)
    }
}
