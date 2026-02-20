package com.aura.music.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Semantic color palette for consistent theming across the app
 * 
 * This defines layers and surfaces for proper visual hierarchy:
 * - background: Main app background
 * - surface: Primary surface (screens, containers)
 * - surfaceElevated: Elevated surfaces (cards that need to stand out)
 * - card: Card backgrounds (list items, content cards)
 * - divider: Separator lines
 * - textPrimary: Main text color
 * - textSecondary: Secondary/subtitle text color
 */
data class ThemePalette(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val card: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color
)

/**
 * Production-grade Light Theme Palette
 * 
 * Uses soft gray-white tones instead of harsh pure white
 * Creates depth through subtle contrast between surfaces
 */
val LightThemePalette = ThemePalette(
    background = Color(0xFFF4F6F8),      // Soft gray-white background
    surface = Color(0xFFFFFFFF),         // Pure white for main surfaces
    surfaceElevated = Color(0xFFF1F3F5), // Slightly darker for elevated elements
    card = Color(0xFFFFFFFF),            // White cards with shadows
    divider = Color(0xFFE2E6EA),         // Subtle divider lines
    textPrimary = Color(0xFF111111),     // Near-black for excellent readability
    textSecondary = Color(0xFF6B7280),   // Medium gray for secondary text
    textTertiary = Color(0xFF9CA3AF)     // Light gray for tertiary text
)

/**
 * Production-grade Dark Theme Palette
 * 
 * Uses layered blacks for depth instead of flat black
 * OLED-friendly with premium look and feel
 */
val DarkThemePalette = ThemePalette(
    background = Color(0xFF0B0B0C),      // Deep black background (OLED friendly)
    surface = Color(0xFF121214),         // Primary surface layer
    surfaceElevated = Color(0xFF1A1A1D), // Elevated surfaces for depth
    card = Color(0xFF18181B),            // Card backgrounds
    divider = Color(0xFF27272A),         // Subtle divider lines
    textPrimary = Color(0xFFF4F4F5),     // Off-white for comfortable reading
    textSecondary = Color(0xFFA1A1AA),   // Light gray for secondary text
    textTertiary = Color(0xFF71717A)     // Medium gray for tertiary text
)

/**
 * Minimal Mode Palette
 * 
 * Extremely minimal with neutral grays for distraction-free experience
 */
val MinimalThemePalette = ThemePalette(
    background = Color(0xFF0D0D0D),      // Pure black
    surface = Color(0xFF1A1A1A),         // Very dark gray
    surfaceElevated = Color(0xFF262626), // Slightly elevated
    card = Color(0xFF1F1F1F),            // Card background
    divider = Color(0xFF4A4A4A),         // Minimal divider
    textPrimary = Color(0xFFE8E8E8),     // Very light gray
    textSecondary = Color(0xFF999999),   // Medium gray
    textTertiary = Color(0xFF707070)     // Neutral gray
)

/**
 * Get semantic palette based on theme mode
 */
fun getSemanticPalette(isDark: Boolean, minimalMode: Boolean = false): ThemePalette {
    return when {
        minimalMode -> MinimalThemePalette
        isDark -> DarkThemePalette
        else -> LightThemePalette
    }
}
