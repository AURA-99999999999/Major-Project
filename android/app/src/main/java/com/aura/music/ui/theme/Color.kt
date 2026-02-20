package com.aura.music.ui.theme

import androidx.compose.ui.graphics.Color

// ============ PRODUCTION DARK THEME COLORS ============
// Premium layered blacks for depth and OLED optimization

// Core dark theme colors - Layered for depth
val DarkBackground = Color(0xFF0B0B0C)       // Deep OLED black
val DarkSurface = Color(0xFF121214)          // Primary surface layer
val DarkSurfaceVariant = Color(0xFF1A1A1D)   // Elevated surfaces
val DarkSurfaceContainer = Color(0xFF18181B) // Card backgrounds

// Text colors for dark theme - Optimized readability
val DarkTextPrimary = Color(0xFFF4F4F5)      // Off-white (not pure white)
val DarkTextSecondary = Color(0xFFA1A1AA)    // Light gray for secondary
val DarkTextTertiary = Color(0xFF71717A)     // Medium gray for tertiary

// UI elements
val DarkPrimary = Color(0xFFFFFFFF)          // White for buttons (will be overridden by accent)
val DarkSecondary = Color(0xFF3D5A80)        // Medium blue
val DarkTertiary = Color(0xFF64B5F6)         // Bright sky blue

val DarkError = Color(0xFFEF5350)
val DarkDivider = Color(0xFF27272A)          // Premium divider color
val DarkOutline = Color(0xFF3D5A80)

// ============ PRODUCTION LIGHT THEME COLORS ============
// Soft tones instead of harsh pure white for premium feel

val LightBackground = Color(0xFFF4F6F8)      // Soft gray-white (not pure white!)
val LightSurface = Color(0xFFFFFFFF)         // Pure white for contrast
val LightSurfaceVariant = Color(0xFFF1F3F5)  // Elevated surfaces
val LightSurfaceContainer = Color(0xFFF0F0F0) // Card backgrounds

// Text colors for light theme - High contrast readability
val LightTextPrimary = Color(0xFF111111)     // Near-black for readability
val LightTextSecondary = Color(0xFF6B7280)   // Medium gray
val LightTextTertiary = Color(0xFF9CA3AF)    // Light gray

val LightPrimary = Color(0xFF1A1A1A)         // Dark for buttons
val LightSecondary = Color(0xFF3D5A80)       // Medium blue
val LightTertiary = Color(0xFF64B5F6)        // Sky blue

val LightError = Color(0xFFB3261E)
val LightDivider = Color(0xFFE2E6EA)         // Subtle divider
val LightOutline = Color(0xFFC0C0C0)

// ============ MINIMAL MODE COLORS ============

val MinimalDarkBackground = Color(0xFF0D0D0D) // Pure black
val MinimalDarkSurface = Color(0xFF1A1A1A) // Very dark gray
val MinimalDarkText = Color(0xFFE8E8E8) // Very light gray
val MinimalAccent = Color(0xFF808080) // Neutral gray

// ============ COMMON COLORS ============

val Error = Color(0xFFEF5350)
val Success = Color(0xFF4CAF50)
val Warning = Color(0xFFFFC107)
val Info = Color(0xFF2196F3)

// AMOLED black for dark mode maximum contrast
val AmoledBlack = Color(0xFF000000)

// Transparent variants
val SurfaceTransparent = Color(0x1A1A2332) // 10% opacity
val DividerTransparent = Color(0x1A2D3F5C) // 10% opacity

// ==== BACKWARD COMPATIBILITY EXPORTS ====
// ⚠️ DEPRECATED: Do NOT use these in new code!
// Use MaterialTheme.colorScheme.* instead for proper dynamic theming
// These are hardcoded to dark theme colors and will break light theme
@Deprecated("Use MaterialTheme.colorScheme.primary instead", ReplaceWith("MaterialTheme.colorScheme.primary"))
val Primary = DarkPrimary

@Deprecated("Use MaterialTheme.colorScheme.onBackground instead", ReplaceWith("MaterialTheme.colorScheme.onBackground"))
val TextPrimary = DarkTextPrimary

@Deprecated("Use MaterialTheme.colorScheme.onSurfaceVariant instead", ReplaceWith("MaterialTheme.colorScheme.onSurfaceVariant"))
val TextSecondary = DarkTextSecondary

@Deprecated("Use MaterialTheme.colorScheme.secondary instead", ReplaceWith("MaterialTheme.colorScheme.secondary"))
val PrimaryVariant = DarkSecondary

@Deprecated("Use MaterialTheme.colorScheme.secondary instead", ReplaceWith("MaterialTheme.colorScheme.secondary"))
val Secondary = DarkSecondary
