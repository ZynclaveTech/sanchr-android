package com.sanchr.core.designsystem.theme

/** The three values `UserPreferences.themeMode` stores, and how they resolve. */
object ThemeMode {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    fun resolveDarkTheme(
        mode: String?,
        systemDark: Boolean,
    ): Boolean =
        when (mode) {
            DARK -> true
            LIGHT -> false
            else -> systemDark
        }
}
