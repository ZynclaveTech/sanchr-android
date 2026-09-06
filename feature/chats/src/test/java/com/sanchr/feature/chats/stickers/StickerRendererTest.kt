package com.sanchr.feature.chats.stickers

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
// Native graphics: under the default shadows Canvas.drawText is a no-op, so a
// renderer that produced a blank square would pass every assertion here.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StickerRendererTest {
    @Test
    fun `a sticker is a square of the size iOS uses`() {
        val bitmap = StickerRenderer.render("😀")
        assertEquals(StickerRenderer.SIZE_PX, bitmap.width)
        assertEquals(StickerRenderer.SIZE_PX, bitmap.height)
    }

    @Test
    fun `the glyph is actually drawn`() {
        val bitmap = StickerRenderer.render("😀")
        val painted =
            (0 until bitmap.width step 4).sumOf { x ->
                (0 until bitmap.height step 4).count { y -> bitmap.getPixel(x, y) != 0 }
            }
        assertTrue(painted > 0, "the sticker came out empty")
    }

    @Test
    fun `the background stays transparent`() {
        // A sticker sits on the bubble, so an opaque square would show as a
        // block of colour around the glyph.
        val bitmap = StickerRenderer.render("😀")
        assertEquals(0, bitmap.getPixel(0, 0))
    }

    /**
     * The send path is the ordinary image one, so what matters is that it
     * hands over a PNG with dimensions — not a sticker-shaped special case.
     */
    @Test
    fun `it prepares an ordinary png attachment`() {
        val prepared = StickerRenderer.prepare("🐶")
        assertEquals("image/png", prepared.mimeType)
        assertEquals(StickerRenderer.SIZE_PX, prepared.width)
        assertEquals(StickerRenderer.SIZE_PX, prepared.height)
        assertTrue(prepared.bytes.isNotEmpty(), "no bytes")
        assertTrue(prepared.fileName?.endsWith(".png") == true, "name was ${prepared.fileName}")
    }

    @Test
    fun `two stickers do not share a filename`() {
        assertTrue(StickerRenderer.prepare("😀").fileName != StickerRenderer.prepare("😀").fileName)
    }

    @Test
    fun `every sticker in every pack renders`() {
        for (pack in StickerPack.entries) {
            for (sticker in pack.stickers) {
                val bitmap = StickerRenderer.render(sticker, sizePx = 32)
                assertEquals(32, bitmap.width, "${pack.id} $sticker")
            }
        }
    }
}
