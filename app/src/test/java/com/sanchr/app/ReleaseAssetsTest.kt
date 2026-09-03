package com.sanchr.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The launcher is the navy composition; the in-app mark is transparent. */
class ReleaseAssetsTest {
    private val res = File(System.getProperty("user.dir"), "src/main/res")
    private val designRes = File(System.getProperty("user.dir"), "../core/designsystem/src/main/res")

    private fun png(file: File): Triple<Int, Int, Int> {
        val b = file.readBytes()

        fun be32(o: Int) = (0 until 4).fold(0) { acc, i -> (acc shl 8) or (b[o + i].toInt() and 0xFF) }
        return Triple(be32(16), be32(20), b[25].toInt())
    }

    @Test
    fun `launcher background is the navy icon ground`() {
        val xml = File(res, "values/ic_launcher_background.xml").readText()
        assertTrue(xml, xml.contains("#0B0E1F"))
    }

    @Test
    fun `launcher foreground is 108dp per density with alpha`() {
        mapOf("mdpi" to 108, "hdpi" to 162, "xhdpi" to 216, "xxhdpi" to 324, "xxxhdpi" to 432).forEach { (d, px) ->
            val (w, h, colour) = png(File(res, "mipmap-$d/ic_launcher_foreground.png"))
            assertEquals(d, px, w)
            assertEquals(d, px, h)
            assertEquals(d, 6, colour)
        }
    }

    @Test
    fun `the in-app mark is transparent at every density`() {
        mapOf("mdpi" to 128, "hdpi" to 192, "xhdpi" to 256, "xxhdpi" to 384, "xxxhdpi" to 512).forEach { (d, px) ->
            val (w, h, colour) = png(File(designRes, "drawable-$d/sanchr_logo.png"))
            assertEquals(d, px, w)
            assertEquals(d, px, h)
            assertEquals(d, 6, colour)
        }
    }
}
