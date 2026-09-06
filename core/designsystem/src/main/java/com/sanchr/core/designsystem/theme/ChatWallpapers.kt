package com.sanchr.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * The wallpapers a chat can use.
 *
 * Stored by name rather than by colour so the palette can be restyled — and
 * so a dark-mode variant can differ — without rewriting what every
 * conversation already chose.
 */
object ChatWallpapers {
    /** Follows the account-wide choice; the account-wide choice falls back to the theme surface. */
    const val DEFAULT = "default"

    /** Names in the order the picker shows them. */
    val NAMES: List<String> = listOf(DEFAULT, "indigo", "cyan", "sand", "slate")

    /**
     * The colour for [name] in the current theme, or null for [DEFAULT] and
     * for any name this build does not know, which means "use the ordinary
     * chat background" rather than a colour picked at random.
     */
    fun colorOf(
        name: String?,
        darkTheme: Boolean,
    ): Color? =
        when (name) {
            "indigo" -> if (darkTheme) Color(0xFF1E1B33) else Color(0xFFEEF0FB)
            "cyan" -> if (darkTheme) Color(0xFF102A2E) else Color(0xFFE7F6F7)
            "sand" -> if (darkTheme) Color(0xFF2A2419) else Color(0xFFFAF3E6)
            "slate" -> if (darkTheme) Color(0xFF1B1F24) else Color(0xFFEDF0F3)
            else -> null
        }

    /** How the name reads on screen. */
    fun displayName(name: String): String = if (name == DEFAULT) "Default" else name.replaceFirstChar { it.uppercase() }
}
