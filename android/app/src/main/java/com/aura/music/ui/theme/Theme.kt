package com.aura.music.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Dark mode color scheme with Material 3 design
 * 
 * @param accentPalette The accent color palette to use
 * @param dynamicColors Optional dynamic colors from album artwork
 * @param gradientTheme Optional gradient theme to apply
 */
private fun createDarkColorScheme(
    accentPalette: AccentPaletteColors,
    dynamicColors: ThemeColors? = null,
    gradientTheme: GradientTheme = GradientTheme.NONE
): androidx.compose.material3.ColorScheme {
    // Priority: Dynamic album color > Gradient theme > Default palette
    val primaryColor = dynamicColors?.vibrant ?: accentPalette.primary
    val secondaryColor = dynamicColors?.vibrantDark ?: accentPalette.secondary
    val tertiaryColor = dynamicColors?.vibrantLight ?: accentPalette.tertiary
    
    // Get semantic palette
    val semanticPalette = DarkThemePalette
    
    // Determine background: dynamic > gradient > default
    val background = when {
        dynamicColors?.dominant != null -> {
            // Blend 10% of dominant color with premium black background
            blendColors(semanticPalette.background, dynamicColors.dominant, 0.10f)
        }
        gradientTheme != GradientTheme.NONE -> {
            // Use gradient start color as base
            val gradientColors = GradientProvider.getGradientColors(gradientTheme, isDark = true)
            gradientColors?.first() ?: semanticPalette.background
        }
        else -> semanticPalette.background
    }
    
    // Surface uses subtle dynamic color or default
    val surface = if (dynamicColors?.mutedDark != null) {
        blendColors(semanticPalette.surface, dynamicColors.mutedDark, 0.15f)
    } else {
        semanticPalette.surface
    }
    
    return darkColorScheme(
        primary = primaryColor,
        secondary = secondaryColor,
        tertiary = tertiaryColor,
        background = background,
        surface = surface,
        surfaceVariant = semanticPalette.surfaceElevated,  // Use elevated surface
        onPrimary = Color.Black,
        onSecondary = semanticPalette.textPrimary,
        onTertiary = Color.Black,
        onBackground = semanticPalette.textPrimary,
        onSurface = semanticPalette.textPrimary,
        onSurfaceVariant = semanticPalette.textSecondary,
        error = DarkError,
        outline = DarkOutline,
        outlineVariant = semanticPalette.divider
    )
}

/**
 * Blend two colors together
 * @param baseColor The base color
 * @param blendColor The color to blend in
 * @param ratio How much of blendColor to use (0.0 to 1.0)
 */
private fun blendColors(baseColor: Color, blendColor: Color, ratio: Float): Color {
    val inverseRatio = 1f - ratio
    return Color(
        red = baseColor.red * inverseRatio + blendColor.red * ratio,
        green = baseColor.green * inverseRatio + blendColor.green * ratio,
        blue = baseColor.blue * inverseRatio + blendColor.blue * ratio,
        alpha = 1f
    )
}

/**
 * Light mode color scheme with Material 3 design
 * 
 * @param accentPalette The accent color palette to use
 * @param dynamicColors Optional dynamic colors from album artwork
 * @param gradientTheme Optional gradient theme to apply
 */
private fun createLightColorScheme(
    accentPalette: AccentPaletteColors,
    dynamicColors: ThemeColors? = null,
    gradientTheme: GradientTheme = GradientTheme.NONE
): androidx.compose.material3.ColorScheme {
    // Priority: Dynamic album color > Gradient theme > Default palette
    val primaryColor = dynamicColors?.vibrant ?: accentPalette.primary
    val secondaryColor = dynamicColors?.vibrantDark ?: accentPalette.secondary
    val tertiaryColor = dynamicColors?.vibrantLight ?: accentPalette.tertiary
    
    // Get semantic palette
    val semanticPalette = LightThemePalette
    
    // Determine background: dynamic > gradient > default
    val background = when {
        dynamicColors?.dominant != null -> {
            // Blend 5% of dominant color with soft light background
            blendColors(semanticPalette.background, dynamicColors.dominant, 0.05f)
        }
        gradientTheme != GradientTheme.NONE -> {
            // Use gradient start color as base
            val gradientColors = GradientProvider.getGradientColors(gradientTheme, isDark = false)
            gradientColors?.first() ?: semanticPalette.background
        }
        else -> semanticPalette.background
    }
    
    // Surface uses subtle dynamic color or default
    val surface = if (dynamicColors?.mutedLight != null) {
        blendColors(semanticPalette.surface, dynamicColors.mutedLight, 0.08f)
    } else {
        semanticPalette.surface
    }
    
    return lightColorScheme(
        primary = primaryColor,
        secondary = secondaryColor,
        tertiary = tertiaryColor,
        background = background,
        surface = surface,
        surfaceVariant = semanticPalette.surfaceElevated,  // Use elevated surface
        onPrimary = Color.White,
        onSecondary = semanticPalette.textPrimary,
        onTertiary = Color.White,
        onBackground = semanticPalette.textPrimary,
        onSurface = semanticPalette.textPrimary,
        onSurfaceVariant = semanticPalette.textSecondary,
        error = LightError,
        outline = LightOutline,
        outlineVariant = semanticPalette.divider
    )
}

/**
 * Minimal mode dark color scheme - reduced visual noise
 */
private fun createMinimalDarkColorScheme():  androidx.compose.material3.ColorScheme {
    val neutralGray = Color(0xFF707070)
    return darkColorScheme(
        primary = MinimalAccent,
        secondary = MinimalAccent,
        tertiary = MinimalAccent,
        background = MinimalDarkBackground,
        surface = MinimalDarkSurface,
        surfaceVariant = Color(0xFF262626),
        onPrimary = MinimalDarkText,
        onSecondary = MinimalDarkText,
        onTertiary = MinimalDarkText,
        onBackground = MinimalDarkText,
        onSurface = MinimalDarkText,
        onSurfaceVariant = Color(0xFF999999),
        error = Color(0xFF999999),
        outline = Color(0xFF4A4A4A),
        outlineVariant = Color(0xFF353535)
    )
}

/**
 * Main theme composable - provides Material 3 theme with dynamic color support
 * 
 * Integrates with ThemeManager for reactive theme updates
 * Automatically updates when theme settings change, without restarting activity
 *
 * @param themeState Current theme state from ThemeManager
 * @param content Composable content to apply theme to
 */
@Composable
fun AuraTheme(
    themeState: ThemeState = ThemeState(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    // Determine if dark theme should be used
    val isDarkTheme = when (themeState.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> {
            val isSystemDarkMode = context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            isSystemDarkMode
        }
    }

    // Get accent palette
    val accentPalette = AccentPaletteProvider.getPalette(themeState.accentColor)
    
    // Get dynamic colors if enabled
    val dynamicColors = if (themeState.dynamicAlbumColors && 
                             themeState.currentDynamicColors.dominant != null) {
        themeState.currentDynamicColors
    } else {
        null
    }

    // Select appropriate color scheme
    val colorScheme = when {
        // Minimal mode uses special color scheme
        themeState.minimalMode -> createMinimalDarkColorScheme()
        // Dynamic colors (Android 12+) - disabled by default for theme control
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && false -> {
            if (isDarkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        // Use custom theme colors based on accent (with optional dynamic album colors & gradients)
        isDarkTheme -> createDarkColorScheme(accentPalette, dynamicColors, themeState.gradientTheme)
        else -> createLightColorScheme(accentPalette, dynamicColors, themeState.gradientTheme)
    }

    // Apply theme to status bar and navigation bar
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController?.isAppearanceLightStatusBars = !isDarkTheme
            insetsController?.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
