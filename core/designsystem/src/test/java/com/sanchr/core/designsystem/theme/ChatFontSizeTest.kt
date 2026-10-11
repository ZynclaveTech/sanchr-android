package com.sanchr.core.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The Font Size slider moved and no text changed size. */
class ChatFontSizeTest {
    private val base = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)

    @Test
    fun `each step is bigger than the one before`() {
        val scales = ChatFontSize.options.map(ChatFontSize::scaleOf)

        assertEquals(scales.sorted(), scales)
        assertEquals(scales.distinct(), scales, "no two steps the same size")
    }

    @Test
    fun `medium is the size the text always was`() {
        assertEquals(1f, ChatFontSize.scaleOf(ChatFontSize.MEDIUM))
        assertSame(base, ChatFontSize.apply(base, ChatFontSize.MEDIUM))
        assertEquals(ChatFontSize.MEDIUM, ChatFontSize.DEFAULT)
    }

    @Test
    fun `small is smaller and large is larger`() {
        assertTrue(ChatFontSize.apply(base, ChatFontSize.SMALL).fontSize.value < base.fontSize.value)
        assertTrue(ChatFontSize.apply(base, ChatFontSize.LARGE).fontSize.value > base.fontSize.value)
        assertTrue(
            ChatFontSize.apply(base, ChatFontSize.EXTRA_LARGE).fontSize.value >
                ChatFontSize.apply(base, ChatFontSize.LARGE).fontSize.value,
        )
    }

    /** Bigger text in the old line height would have its lines touching. */
    @Test
    fun `the line height grows with the text`() {
        val large = ChatFontSize.apply(base, ChatFontSize.EXTRA_LARGE)

        assertEquals(base.lineHeight.value / base.fontSize.value, large.lineHeight.value / large.fontSize.value, 0.001f)
    }

    @Test
    fun `a size this build does not know is drawn as usual`() {
        assertEquals(1f, ChatFontSize.scaleOf("gigantic"))
        assertEquals(1f, ChatFontSize.scaleOf(""))
    }

    @Test
    fun `the slider offers the four sizes in order`() {
        assertEquals(listOf("small", "medium", "large", "extra_large"), ChatFontSize.options)
    }
}
