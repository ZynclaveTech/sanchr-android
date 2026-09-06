package com.sanchr.core.designsystem.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ChatWallpapersTest {
    @Test
    fun `default has no colour of its own, so the chat keeps the theme background`() {
        assertNull(ChatWallpapers.colorOf(ChatWallpapers.DEFAULT, darkTheme = false))
        assertNull(ChatWallpapers.colorOf(null, darkTheme = false))
    }

    @Test
    fun `every offered wallpaper resolves in both themes`() {
        ChatWallpapers.NAMES.filterNot { it == ChatWallpapers.DEFAULT }.forEach { name ->
            assertNotNull(ChatWallpapers.colorOf(name, darkTheme = false), "$name has no light colour")
            assertNotNull(ChatWallpapers.colorOf(name, darkTheme = true), "$name has no dark colour")
        }
    }

    @Test
    fun `dark and light differ, so a wallpaper is not glaring in dark mode`() {
        ChatWallpapers.NAMES.filterNot { it == ChatWallpapers.DEFAULT }.forEach { name ->
            assertNotEquals(
                ChatWallpapers.colorOf(name, darkTheme = false),
                ChatWallpapers.colorOf(name, darkTheme = true),
                "$name is the same colour in both themes",
            )
        }
    }

    @Test
    fun `a name this build does not know falls back to the ordinary background`() {
        assertNull(ChatWallpapers.colorOf("aurora", darkTheme = false))
    }

    @Test
    fun `names read properly on screen`() {
        assertEquals("Default", ChatWallpapers.displayName(ChatWallpapers.DEFAULT))
        assertEquals("Indigo", ChatWallpapers.displayName("indigo"))
    }
}
