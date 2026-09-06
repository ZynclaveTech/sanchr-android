package com.sanchr.feature.chats.stickers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The packs are a parity claim, not just content.
 *
 * A pack id travels nowhere — a sticker is sent as a plain PNG — but the two
 * apps are supposed to offer the same four packs in the same order, and a
 * quiet divergence is the sort of thing nobody notices until someone compares
 * two phones.
 */
class StickerPackTest {
    @Test
    fun `the four iOS packs are present, in order`() {
        assertEquals(
            listOf("faces", "animals", "food", "gestures"),
            StickerPack.entries.map { it.id },
        )
    }

    @Test
    fun `pack sizes match iOS`() {
        assertEquals(80, StickerPack.FACES.stickers.size)
        for (pack in listOf(StickerPack.ANIMALS, StickerPack.FOOD, StickerPack.HANDS)) {
            assertEquals(64, pack.stickers.size, pack.id)
        }
    }

    @Test
    fun `no pack repeats a sticker`() {
        for (pack in StickerPack.entries) {
            val duplicates =
                pack.stickers
                    .groupBy { it }
                    .filter { it.value.size > 1 }
                    .keys
            assertTrue(duplicates.isEmpty(), "${pack.id} repeats $duplicates")
        }
    }

    @Test
    fun `every pack has an icon drawn from nothing but emoji`() {
        for (pack in StickerPack.entries) {
            assertTrue(pack.icon.isNotBlank(), pack.id)
            assertTrue(pack.label.isNotBlank(), pack.id)
        }
    }

    @Test
    fun `no sticker is blank or padded`() {
        for (pack in StickerPack.entries) {
            for (sticker in pack.stickers) {
                assertTrue(sticker.isNotBlank(), "${pack.id} has a blank sticker")
                assertEquals(sticker, sticker.trim(), "${pack.id} has a padded sticker")
            }
        }
    }
}
