package com.aura.music.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Theme modes enumeration
 */
enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

/**
 * Available accent colors
 */
enum class AccentColor(val id: String, val displayName: String) {
    // Original colors
    PURPLE("purple", "Purple"),
    BLUE("blue", "Blue"),
    GREEN("green", "Green"),
    ROSE("rose", "Rose"),
    AMBER("amber", "Amber"),
    CYAN("cyan", "Cyan"),
    INDIGO("indigo", "Indigo"),
    
    // New aesthetic colors
    OCEAN("ocean", "Ocean Blue"),
    CORAL("coral", "Coral"),
    LAVENDER("lavender", "Lavender"),
    MINT("mint", "Mint"),
    PEACH("peach", "Peach"),
    CRIMSON("crimson", "Crimson"),
    TEAL("teal", "Teal"),
    EMERALD("emerald", "Emerald"),
    MAGENTA("magenta", "Magenta"),
    SKY("sky", "Sky Blue"),
    SUNSET("sunset", "Sunset Orange"),
    ORCHID("orchid", "Orchid"),
    TURQUOISE("turquoise", "Turquoise"),
    CHERRY("cherry", "Cherry Red");

    companion object {
        fun fromId(id: String): AccentColor = entries.firstOrNull { it.id == id } ?: BLUE
    }
}

/**
 * Gradient theme options
 */
enum class GradientTheme(val id: String, val displayName: String) {
    NONE("none", "None"),
    
    // Original gradients
    AURORA_GLOW("aurora", "Aurora Glow"),
    SUNSET_VIBES("sunset", "Sunset Vibes"),
    
    // New aesthetic gradients
    OCEAN_BREEZE("ocean_breeze", "Ocean Breeze"),
    MIDNIGHT_DREAM("midnight", "Midnight Dream"),
    CHERRY_BLOSSOM("cherry_blossom", "Cherry Blossom"),
    FOREST_MIST("forest", "Forest Mist"),
    COSMIC_PURPLE("cosmic", "Cosmic Purple"),
    NORTHERN_LIGHTS("northern", "Northern Lights"),
    VELVET_ROSE("velvet", "Velvet Rose"),
    TROPICAL_PARADISE("tropical", "Tropical Paradise"),
    SAKURA_DREAM("sakura", "Sakura Dream");

    companion object {
        fun fromId(id: String): GradientTheme = entries.firstOrNull { it.id == id } ?: NONE
    }
}

/**
 * Accent color palette definition
 */
data class AccentPaletteColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val light: Color,
    val dark: Color
)

/**
 * Dynamic theme colors extracted from album artwork
 */
data class ThemeColors(
    val vibrant: Color? = null,
    val vibrantDark: Color? = null,
    val vibrantLight: Color? = null,
    val muted: Color? = null,
    val mutedDark: Color? = null,
    val mutedLight: Color? = null,
    val dominant: Color? = null
) {
    /**
     * Get the best primary color for player background
     * Priority: vibrant > vibrant dark > muted > muted dark > dominant
     */
    fun getPrimaryColor(): Color? = vibrant ?: vibrantDark ?: muted ?: mutedDark ?: dominant
    
    /**
     * Get the best secondary color for gradient blending
     * Priority: muted > vibrant dark > muted dark > dominant
     */
    fun getSecondaryColor(): Color? = muted ?: vibrantDark ?: mutedDark ?: dominant
    
    /**
     * Check if any colors were extracted
     */
    fun hasColors(): Boolean = listOf(vibrant, vibrantDark, vibrantLight, muted, mutedDark, mutedLight, dominant)
        .any { it != null }
}

/**
 * Complete theme state
 */
data class ThemeState(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentColor: AccentColor = AccentColor.BLUE,
    val gradientTheme: GradientTheme = GradientTheme.NONE,
    val dynamicAlbumColors: Boolean = true,
    val minimalMode: Boolean = false,
    val currentDynamicColors: ThemeColors = ThemeColors()
)

/**
 * Gradient definition
 */
data class GradientBrush(
    val startColor: Color,
    val endColor: Color,
    val name: String
)
