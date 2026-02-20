package com.aura.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Extension functions for accessing themed colors throughout the app
 * Provides convenient access to theme-aware colors based on current Material 3 theme
 */

/**
 * Get primary text color based on current theme
 */
@Composable
fun getPrimaryTextColor(): Color {
    return MaterialTheme.colorScheme.onSurface
}

/**
 * Get secondary text color based on current theme
 */
@Composable
fun getSecondaryTextColor(): Color {
    return MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Get background color based on current theme
 */
@Composable
fun getBackgroundColor(): Color {
    return MaterialTheme.colorScheme.background
}

/**
 * Get surface color based on current theme
 */
@Composable
fun getSurfaceColor(): Color {
    return MaterialTheme.colorScheme.surface
}

/**
 * Get primary accent color based on current theme
 */
@Composable
fun getPrimaryAccentColor(): Color {
    return MaterialTheme.colorScheme.primary
}

/**
 * Get secondary accent color based on current theme
 */
@Composable
fun getSecondaryAccentColor(): Color {
    return MaterialTheme.colorScheme.secondary
}

/**
 * Get tertiary accent color based on current theme
 */
@Composable
fun getTertiaryAccentColor(): Color {
    return MaterialTheme.colorScheme.tertiary
}

/**
 * Get error color based on current theme
 */
@Composable
fun getErrorColor(): Color {
    return MaterialTheme.colorScheme.error
}
