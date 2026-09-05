package com.sanchr.core.network.link

/** What a page says about itself, from its Open Graph / Twitter tags with `<title>` as the fallback. */
data class ParsedPage(
    val title: String?,
    val imageUrl: String?,
)

/** A small, dependency-free reader of the few `<meta>` tags a preview card needs. */
object OpenGraphParser {
    private val META = Regex("""(?is)<meta\b[^>]*>""")
    private val ATTR = Regex("""(?is)\b(property|name|content)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""")
    private val TITLE = Regex("""(?is)<title[^>]*>(.*?)</title>""")
    private val ENTITY = Regex("""&(#x[0-9a-fA-F]+|#\d+|amp|lt|gt|quot|apos|nbsp);""")
    private const val MAX_TITLE = 300

    fun parse(
        html: String,
        pageUrl: String,
    ): ParsedPage {
        val meta = mutableMapOf<String, String>()
        for (tag in META.findAll(html)) {
            var key: String? = null
            var content: String? = null
            for (attr in ATTR.findAll(tag.value)) {
                val value = attr.groupValues[2].ifEmpty { attr.groupValues[3] }.ifEmpty { attr.groupValues[4] }
                when (attr.groupValues[1].lowercase()) {
                    "property", "name" -> key = value.lowercase()
                    "content" -> content = value
                }
            }
            if (key != null && content != null && key !in meta) meta[key] = content
        }
        val title =
            (meta["og:title"] ?: meta["twitter:title"] ?: TITLE.find(html)?.groupValues?.get(1))
                ?.let(::decode)
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?.take(MAX_TITLE)
                ?.takeIf { it.isNotEmpty() }
        val image = (meta["og:image"] ?: meta["og:image:url"] ?: meta["twitter:image"])?.let { resolve(decode(it).trim(), pageUrl) }
        return ParsedPage(title = title, imageUrl = image)
    }

    /** Resolves a possibly relative image reference against the page, dropping anything that is not http(s). */
    internal fun resolve(
        ref: String,
        pageUrl: String,
    ): String? {
        if (ref.isEmpty()) return null
        // Any other scheme (data:, javascript:, ftp:) is not something to fetch.
        if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(ref) && !ref.startsWith("http", ignoreCase = true)) return null
        val absolute =
            when {
                ref.startsWith("http://", true) || ref.startsWith("https://", true) -> ref
                ref.startsWith("//") -> pageUrl.substringBefore("://") + ":" + ref
                ref.startsWith("/") -> origin(pageUrl) + ref
                else -> pageUrl.substringBeforeLast('/', pageUrl) + "/" + ref
            }
        return absolute.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
    }

    private fun origin(url: String): String {
        val scheme = url.substringBefore("://")
        val host = url.substringAfter("://").substringBefore('/')
        return "$scheme://$host"
    }

    @Suppress("MagicNumber")
    internal fun decode(s: String): String =
        ENTITY.replace(s) { m ->
            when (val e = m.groupValues[1]) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                "nbsp" -> " "
                else ->
                    runCatching {
                        val code = if (e.startsWith("#x", true)) e.substring(2).toInt(16) else e.substring(1).toInt()
                        String(Character.toChars(code))
                    }.getOrDefault(m.value)
            }
        }
}
