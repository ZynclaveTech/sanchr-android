package com.sanchr.core.network.link

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OpenGraphParserTest {
    @Test
    fun `prefers og tags, decodes entities, and resolves relative images`() {
        val html =
            """
            <html><head><title>Fallback &amp; title</title>
            <meta property="og:title" content="Tom &amp; Jerry &#8212; ep 1" />
            <meta property='og:image' content='/img/cover.jpg'>
            </head></html>
            """.trimIndent()
        val page = OpenGraphParser.parse(html, "https://example.com/shows/1?x=y")
        assertEquals("Tom & Jerry — ep 1", page.title)
        assertEquals("https://example.com/img/cover.jpg", page.imageUrl)
    }

    @Test
    fun `falls back to the title tag and twitter tags, and drops non-http images`() {
        val html = """<meta name="twitter:image" content="data:image/png;base64,AAAA"><title>  Just  a page </title>"""
        val page = OpenGraphParser.parse(html, "https://example.com/")
        assertEquals("Just a page", page.title)
        assertNull(page.imageUrl)
        assertEquals("https://cdn.example.com/a.png", OpenGraphParser.resolve("//cdn.example.com/a.png", "https://example.com/p"))
        assertEquals("https://example.com/dir/a.png", OpenGraphParser.resolve("a.png", "https://example.com/dir/page"))
    }

    @Test
    fun `garbage yields nothing`() {
        val page = OpenGraphParser.parse("<p>hello", "https://example.com")
        assertNull(page.title)
        assertNull(page.imageUrl)
    }
}
