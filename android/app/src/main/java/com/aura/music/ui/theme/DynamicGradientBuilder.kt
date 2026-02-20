package com.aura.music.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Creates dynamic gradient backgrounds from album artwork colors
 * Ensures smooth visual transitions and maintains readability
 */
object DynamicGradientBuilder {

    /**
     * Build elegant gradient from album color
     * Creates 4-stop gradient for smooth depth transition
     * 
     * @param primaryColor Dominant color from album artwork
     * @param isDarkTheme Whether current theme is dark
     * @return Vertical gradient brush
     */
    fun buildAlbumGradient(
        primaryColor: Color,
        isDarkTheme: Boolean = true
    ): Brush {
        return if (isDarkTheme) {
            // Dark theme: Strong color at top, fading to black
            Brush.verticalGradient(
                colors = listOf(
                    ColorBlendingUtils.lighten(primaryColor, 0.15f),  // Highlight
                    primaryColor,                                     // Primary
                    ColorBlendingUtils.darken(primaryColor, 0.3f),   // Shadow
                    Color(0xFF0A0A0A)                                // Near black
                )
            )
        } else {
            // Light theme: Lighter tints for readability
            Brush.verticalGradient(
                colors = listOf(
                    ColorBlendingUtils.lighten(primaryColor, 0.35f), // Very light
                    ColorBlendingUtils.lighten(primaryColor, 0.15f), // Light
                    primaryColor,                                     // Primary
                    ColorBlendingUtils.desaturate(primaryColor, 0.2f) // Muted
                )
            )
        }
    }

    /**
     * Build gradient with theme integration
     * Blends album color with theme palette for cohesion
     * 
     * @param albumColor Dominant color from album artwork
     * @param themeColor Theme palette color
     * @param blendAlpha Weight of album color (0f-1f)
     * @param isDarkTheme Whether current theme is dark
     * @return Blended gradient brush
     */
    fun buildBlendedGradient(
        albumColor: Color,
        themeColor: Color,
        blendAlpha: Float = 0.7f,
        isDarkTheme: Boolean = true
    ): Brush {
        val blended1 = ColorBlendingUtils.blendColors(albumColor, themeColor, blendAlpha)
        val blended2 = ColorBlendingUtils.blendColors(albumColor, themeColor, blendAlpha * 0.8f)
        val blended3 = ColorBlendingUtils.blendColors(albumColor, themeColor, blendAlpha * 0.5f)
        
        return if (isDarkTheme) {
            Brush.verticalGradient(
                colors = listOf(
                    ColorBlendingUtils.lighten(blended1, 0.1f),
                    blended2,
                    ColorBlendingUtils.darken(blended3, 0.25f),
                    Color(0xFF0A0A0A)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    ColorBlendingUtils.lighten(blended1, 0.3f),
                    ColorBlendingUtils.lighten(blended2, 0.15f),
                    blended3,
                    ColorBlendingUtils.desaturate(blended3, 0.15f)
                )
            )
        }
    }

    /**
     * Build gradient for complementary color pairing
     * Uses color harmony for aesthetic appeal
     * 
     * @param primaryColor Main album color
     * @param isDarkTheme Whether current theme is dark
     * @return Harmonic gradient brush
     */
    fun buildComplementaryGradient(
        primaryColor: Color,
        isDarkTheme: Boolean = true
    ): Brush {
        val saturated = ColorBlendingUtils.saturate(primaryColor, 0.15f)
        val darkVersion = ColorBlendingUtils.darken(primaryColor, 0.25f)
        val darkSaturated = ColorBlendingUtils.saturate(darkVersion, 0.1f)
        
        return if (isDarkTheme) {
            Brush.verticalGradient(
                colors = listOf(
                    ColorBlendingUtils.lighten(saturated, 0.1f),
                    saturated,
                    darkSaturated,
                    Color(0xFF0A0A0A)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    ColorBlendingUtils.lighten(saturated, 0.25f),
                    ColorBlendingUtils.lighten(primaryColor, 0.1f),
                    primaryColor,
                    ColorBlendingUtils.desaturate(darkVersion, 0.2f)
                )
            )
        }
    }

    /**
     * Build a readability overlay for text contrast
     * Adjusts opacity based on background brightness
     * 
     * @param backgroundColor The background gradient's primary color
     * @return Overlay color with appropriate alpha
     */
    fun buildReadabilityOverlay(backgroundColor: Color): Color {
        val brightness = ColorBlendingUtils.getPerceivedBrightness(backgroundColor)
        
        // More opaque overlay for lighter backgrounds, subtle for dark
        val alpha = when {
            brightness > 0.7f -> 0.5f   // Light background: strong overlay
            brightness > 0.5f -> 0.35f  // Medium: moderate overlay
            else -> 0.2f                 // Dark: subtle overlay
        }
        
        return Color(0xFF000000).copy(alpha = alpha)
    }

    /**
     * Build gradient with consistency across theme changes
     * Ensures smooth transitions when user switches songs
     * 
     * @param primaryColor Album artwork color
     * @param previousColor Last gradient's color (for smooth transition)
     * @param isDarkTheme Whether current theme is dark
     * @return Transition-aware gradient
     */
    fun buildTransitionGradient(
        primaryColor: Color,
        previousColor: Color? = null,
        isDarkTheme: Boolean = true
    ): Brush {
        // Use previous color if available, blend for smoother transition
        val blendedColor = previousColor?.let {
            ColorBlendingUtils.blendColors(primaryColor, it, 0.8f)
        } ?: primaryColor
        
        return buildAlbumGradient(blendedColor, isDarkTheme)
    }
}
