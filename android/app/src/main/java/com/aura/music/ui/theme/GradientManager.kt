package com.aura.music.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Manages gradient themes for premium visual effects
 */
object GradientManager {

    /**
     * Get gradient brush by theme
     */
    fun getGradient(gradientTheme: GradientTheme): GradientBrush {
        return when (gradientTheme) {
            GradientTheme.NONE -> noGradient()
            GradientTheme.AURORA_GLOW -> auroraGlowGradient()
            GradientTheme.SUNSET_VIBES -> sunsetVibesGradient()
            GradientTheme.OCEAN_BREEZE -> oceanBreezeGradient()
            GradientTheme.MIDNIGHT_DREAM -> midnightDreamGradient()
            GradientTheme.CHERRY_BLOSSOM -> cherryBlossomGradient()
            GradientTheme.FOREST_MIST -> forestMistGradient()
            GradientTheme.COSMIC_PURPLE -> cosmicPurpleGradient()
            GradientTheme.NORTHERN_LIGHTS -> northernLightsGradient()
            GradientTheme.VELVET_ROSE -> velvetRoseGradient()
            GradientTheme.TROPICAL_PARADISE -> tropicalParadiseGradient()
            GradientTheme.SAKURA_DREAM -> sakuraDreamGradient()
        }
    }

    /**
     * Aurora Glow: Purple to Blue gradient
     * Represents a mystical, energetic aurora
     */
    private fun auroraGlowGradient(): GradientBrush {
        return GradientBrush(
            startColor = Color(0xFF9C27B0), // Purple
            endColor = Color(0xFF2196F3), // Blue
            name = "Aurora Glow"
        )
    }

    /**
     * Sunset Vibes: Orange to Pink gradient
     * Represents a warm, relaxing sunset
     */
    private fun sunsetVibesGradient(): GradientBrush {
        return GradientBrush(
            startColor = Color(0xFFFF7043), // Orange
            endColor = Color(0xFFE91E63), // Pink
            name = "Sunset Vibes"
        )
    }

    /**
     * No gradient - for minimal mode or when disabled
     */
    private fun noGradient(): GradientBrush {
        return GradientBrush(
            startColor = Color.Transparent,
            endColor = Color.Transparent,
            name = "None"
        )
    }

    /**
     * Ocean Breeze: Turquoise to Deep Blue gradient
     * Calming ocean vibes
     */
    private fun oceanBreezeGradient(): GradientBrush {
        return GradientBrush(
            startColor = Color(0xFF06B6D4),
            endColor = Color(0xFF0E7490),
            name = "Ocean Breeze"
        )
    }

    /**
     * Midnight Dream: Deep Blue to Purple gradient
     * Mysterious night sky
     */
    private fun midnightDreamGradient(): GradientBrush {
        return GradientBrush(
            startColor = Color(0xFF1E1B4B),
            endColor = Color(0xFF581C87),
            name = "Midnight Dream"
        )
    }

    /**
     * Cherry Blossom: Soft Pink to Rose gradient
     * Delicate spring vibes
     */
    private fun cherryBlossomGradient(): GradientBrush {
        return GradientBrush(
            startColor = Color(0xFFFBCFE8),
            endColor = Color(0xFFDB2777),
            name = "Cherry Blossom"
        )
    }

    /**
     * Forest Mist: Emerald to Teal gradient
     * Fresh natural atmosphere
     */
    private fun forestMistGradient(): GradientBrush {
        return GradientBrush(
            startColor = Color(0xFF059669),
            endColor = Color(0xFF064E3B),
            name = "Forest Mist"
        )
    }

    /**
     * Cosmic Purple: Violet to Magenta gradient
     * Ethereal galaxy atmosphere
     */
    private fun cosmicPurpleGradient(): GradientBrush {
        return GradientBrush(
            startColor = Color(0xFF7C3AED),
            endColor = Color(0xFF4C1D95),
            name = "Cosmic Purple"
        )
    }

    /**
     * Northern Lights: Green to Blue aurora gradient
     * Magical aurora borealis
     */
    private fun northernLightsGradient(): GradientBrush {
        return GradientBrush(
            startColor = Color(0xFF10B981),
            endColor = Color(0xFF0E7490),
            name = "Northern Lights"
        )
    }

    /**
     * Velvet Rose: Deep Rose to Burgundy gradient
     * Luxurious elegance
     */
    private fun velvetRoseGradient(): GradientBrush {
        return GradientBrush(
            startColor = Color(0xFFBE185D),
            endColor = Color(0xFF4C0519),
            name = "Velvet Rose"
        )
    }

    /**
     * Tropical Paradise: Turquoise to Coral gradient
     * Vibrant tropical beach
     */
    private fun tropicalParadiseGradient(): GradientBrush {
        return GradientBrush(
            startColor = Color(0xFF14B8A6),
            endColor = Color(0xFFD97706),
            name = "Tropical Paradise"
        )
    }

    /**
     * Sakura Dream: Pink cherry blossom gradient
     * Soft dreamy Japanese spring
     */
    private fun sakuraDreamGradient(): GradientBrush {
        return GradientBrush(
            startColor = Color(0xFFFCE7F3),
            endColor = Color(0xFFEC4899),
            name = "Sakura Dream"
        )
    }

    /**
     * Get all available gradients
     */
    fun getAllGradients(): List<GradientTheme> = listOf(
        GradientTheme.NONE,
        GradientTheme.AURORA_GLOW,
        GradientTheme.SUNSET_VIBES,
        GradientTheme.OCEAN_BREEZE,
        GradientTheme.MIDNIGHT_DREAM,
        GradientTheme.CHERRY_BLOSSOM,
        GradientTheme.FOREST_MIST,
        GradientTheme.COSMIC_PURPLE,
        GradientTheme.NORTHERN_LIGHTS,
        GradientTheme.VELVET_ROSE,
        GradientTheme.TROPICAL_PARADISE,
        GradientTheme.SAKURA_DREAM
    )
}
