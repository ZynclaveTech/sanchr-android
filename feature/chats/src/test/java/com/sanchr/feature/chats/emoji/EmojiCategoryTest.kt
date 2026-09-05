package com.sanchr.feature.chats.emoji

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmojiCategoryTest {
    @Test
    fun `the picker offers iOS's nine categories, in the same order`() {
        assertEquals(
            listOf("Smileys", "People", "Animals", "Food", "Activity", "Travel", "Objects", "Symbols", "Flags"),
            EmojiCategory.entries.map { it.label },
        )
    }

    @Test
    fun `every category is populated, and no emoji repeats inside one`() {
        EmojiCategory.entries.forEach { category ->
            assertTrue(category.emojis.size >= 60, "${category.label} has only ${category.emojis.size}")
            assertEquals(category.emojis.size, category.emojis.distinct().size, "${category.label} repeats an emoji")
            assertTrue(category.emojis.none { it.isBlank() }, "${category.label} has a blank entry")
        }
    }
}
