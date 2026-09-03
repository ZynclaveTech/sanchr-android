package com.sanchr.core.designsystem.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test fun `dark wins over a light system`() = assertTrue(ThemeMode.resolveDarkTheme("dark", systemDark = false))

    @Test fun `light wins over a dark system`() = assertFalse(ThemeMode.resolveDarkTheme("light", systemDark = true))

    @Test
    fun `system follows the system`() {
        assertTrue(ThemeMode.resolveDarkTheme("system", systemDark = true))
        assertFalse(ThemeMode.resolveDarkTheme("system", systemDark = false))
    }

    @Test
    fun `unknown and null read as system`() {
        assertTrue(ThemeMode.resolveDarkTheme("purple", systemDark = true))
        assertFalse(ThemeMode.resolveDarkTheme(null, systemDark = false))
    }
}
