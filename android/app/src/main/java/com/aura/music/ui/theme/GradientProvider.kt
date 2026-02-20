package com.aura.music.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Production-grade gradient definitions for global theming
 * 
 * Gradients are applied to:
 * - Main screen backgrounds
 * - Player screen
 * - Mini player bar
 * - Profile header
 * 
 * Cards and surfaces maintain solid colors for readability
 */
object GradientProvider {
    
    /**
     * Aurora Glow - Purple to deep blue gradient
     * Creates a mystical, premium atmosphere
     */
    fun getAuroraGlow(isDark: Boolean = true): Brush {
        return if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF6B46C1),  // Deep purple
                    Color(0xFF4C1D95),  // Darker purple
                    Color(0xFF1E3A8A),  // Deep blue
                    Color(0xFF0F172A)   // Almost black
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFA78BFA),  // Light purple
                    Color(0xFF8B5CF6),  // Medium purple
                    Color(0xFF6366F1),  // Indigo
                    Color(0xFF3B82F6)   // Blue
                )
            )
        }
    }
    
    /**
     * Sunset Vibes - Orange to pink gradient
     * Warm, energetic atmosphere
     */
    fun getSunsetVibes(isDark: Boolean = true): Brush {
        return if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFEA580C),  // Orange
                    Color(0xFFC2410C),  // Darker orange
                    Color(0xFF9F1239),  // Deep rose
                    Color(0xFF4C0519)   // Almost black rose
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFED7AA),  // Light orange
                    Color(0xFFFB923C),  // Medium orange
                    Color(0xFFF97316),  // Orange
                    Color(0xFFF43F5E)   // Rose/pink
                )
            )
        }
    }

    /**
     * Ocean Breeze - Turquoise to deep blue
     * Calm, refreshing ocean vibes
     */
    fun getOceanBreeze(isDark: Boolean = true): Brush {
        return if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF06B6D4),  // Cyan
                    Color(0xFF0891B2),  // Darker cyan
                    Color(0xFF0E7490),  // Deep teal
                    Color(0xFF144552)   // Dark ocean
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF67E8F9),  // Light cyan
                    Color(0xFF22D3EE),  // Bright cyan
                    Color(0xFF06B6D4),  // Cyan
                    Color(0xFF0891B2)   // Medium cyan
                )
            )
        }
    }

    /**
     * Midnight Dream - Deep blue to purple
     * Mysterious, elegant night sky
     */
    fun getMidnightDream(isDark: Boolean = true): Brush {
        return if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF1E1B4B),  // Deep indigo
                    Color(0xFF312E81),  // Darker indigo
                    Color(0xFF4C1D95),  // Deep purple
                    Color(0xFF581C87)   // Dark purple
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF818CF8),  // Light indigo
                    Color(0xFF6366F1),  // Indigo
                    Color(0xFF8B5CF6),  // Purple
                    Color(0xFF7C3AED)   // Darker purple
                )
            )
        }
    }

    /**
     * Cherry Blossom - Soft pink to rose
     * Delicate, romantic spring vibes
     */
    fun getCherryBlossom(isDark: Boolean = true): Brush {
        return if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFDB2777),  // Pink
                    Color(0xFF9F1239),  // Dark pink
                    Color(0xFF881337),  // Deep rose
                    Color(0xFF4C0519)   // Almost black rose
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFCE7F3),  // Very light pink
                    Color(0xFFFBCFE8),  // Light pink
                    Color(0xFFF9A8D4),  // Medium pink
                    Color(0xFFF472B6)   // Rose pink
                )
            )
        }
    }

    /**
     * Forest Mist - Emerald to teal
     * Fresh, natural forest atmosphere
     */
    fun getForestMist(isDark: Boolean = true): Brush {
        return if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF059669),  // Emerald
                    Color(0xFF047857),  // Dark emerald
                    Color(0xFF065F46),  // Deep green
                    Color(0xFF064E3B)   // Forest dark
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF6EE7B7),  // Light emerald
                    Color(0xFF34D399),  // Emerald
                    Color(0xFF10B981),  // Green
                    Color(0xFF059669)   // Dark emerald
                )
            )
        }
    }

    /**
     * Cosmic Purple - Violet to magenta
     * Ethereal, galaxy-inspired atmosphere
     */
    fun getCosmicPurple(isDark: Boolean = true): Brush {
        return if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF7C3AED),  // Violet
                    Color(0xFF6D28D9),  // Purple
                    Color(0xFF5B21B6),  // Deep purple
                    Color(0xFF4C1D95)   // Dark purple
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFC084FC),  // Light violet
                    Color(0xFFA855F7),  // Purple
                    Color(0xFF9333EA),  // Bright purple
                    Color(0xFF7E22CE)   // Deep purple
                )
            )
        }
    }

    /**
     * Northern Lights - Green to blue aurora
     * Magical aurora borealis effect
     */
    fun getNorthernLights(isDark: Boolean = true): Brush {
        return if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF10B981),  // Emerald
                    Color(0xFF14B8A6),  // Teal
                    Color(0xFF0891B2),  // Cyan
                    Color(0xFF0E7490)   // Deep cyan
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF6EE7B7),  // Light emerald
                    Color(0xFF5EEAD4),  // Light teal
                    Color(0xFF67E8F9),  // Light cyan
                    Color(0xFF22D3EE)   // Bright cyan
                )
            )
        }
    }

    /**
     * Velvet Rose - Deep rose to burgundy
     * Luxurious, elegant rose tones
     */
    fun getVelvetRose(isDark: Boolean = true): Brush {
        return if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFBE185D),  // Rose
                    Color(0xFF9F1239),  // Dark rose
                    Color(0xFF881337),  // Burgundy
                    Color(0xFF4C0519)   // Deep burgundy
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFDA4AF),  // Light rose
                    Color(0xFFFB7185),  // Rose
                    Color(0xFFF43F5E),  // Bright rose
                    Color(0xFFE11D48)   // Deep rose
                )
            )
        }
    }

    /**
     * Tropical Paradise - Turquoise to coral
     * Vibrant tropical beach vibes
     */
    fun getTropicalParadise(isDark: Boolean = true): Brush {
        return if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF14B8A6),  // Teal
                    Color(0xFF0D9488),  // Dark teal
                    Color(0xFFD97706),  // Amber
                    Color(0xFFB45309)   // Dark amber
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF5EEAD4),  // Light teal
                    Color(0xFF2DD4BF),  // Turquoise
                    Color(0xFFFBBF24),  // Light amber
                    Color(0xFFF59E0B)   // Amber
                )
            )
        }
    }

    /**
     * Sakura Dream - Pink cherry blossom gradient
     * Soft dreamy Japanese spring
     */
    fun getSakuraDream(isDark: Boolean = true): Brush {
        return if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFEC4899),  // Pink
                    Color(0xFFDB2777),  // Darker pink
                    Color(0xFFA855F7),  // Purple
                    Color(0xFF9333EA)   // Deep purple
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFCE7F3),  // Very light pink
                    Color(0xFFFAD5E7),  // Soft pink
                    Color(0xFFF9A8D4),  // Light pink
                    Color(0xFFF472B6)   // Medium pink
                )
            )
        }
    }
    
    /**
     * Get gradient brush based on theme selection
     */
    fun getGradient(gradientTheme: GradientTheme, isDark: Boolean = true): Brush? {
        return when (gradientTheme) {
            GradientTheme.AURORA_GLOW -> getAuroraGlow(isDark)
            GradientTheme.SUNSET_VIBES -> getSunsetVibes(isDark)
            GradientTheme.OCEAN_BREEZE -> getOceanBreeze(isDark)
            GradientTheme.MIDNIGHT_DREAM -> getMidnightDream(isDark)
            GradientTheme.CHERRY_BLOSSOM -> getCherryBlossom(isDark)
            GradientTheme.FOREST_MIST -> getForestMist(isDark)
            GradientTheme.COSMIC_PURPLE -> getCosmicPurple(isDark)
            GradientTheme.NORTHERN_LIGHTS -> getNorthernLights(isDark)
            GradientTheme.VELVET_ROSE -> getVelvetRose(isDark)
            GradientTheme.TROPICAL_PARADISE -> getTropicalParadise(isDark)
            GradientTheme.SAKURA_DREAM -> getSakuraDream(isDark)
            GradientTheme.NONE -> null
        }
    }
    
    /**
     * Get gradient as color list for Compose backgrounds
     */
    fun getGradientColors(gradientTheme: GradientTheme, isDark: Boolean = true): List<Color>? {
        return when (gradientTheme) {
            GradientTheme.AURORA_GLOW -> if (isDark) {
                listOf(
                    Color(0xFF6B46C1),
                    Color(0xFF4C1D95),
                    Color(0xFF1E3A8A),
                    Color(0xFF0F172A)
                )
            } else {
                listOf(
                    Color(0xFFA78BFA),
                    Color(0xFF8B5CF6),
                    Color(0xFF6366F1),
                    Color(0xFF3B82F6)
                )
            }
            GradientTheme.SUNSET_VIBES -> if (isDark) {
                listOf(
                    Color(0xFFEA580C),
                    Color(0xFFC2410C),
                    Color(0xFF9F1239),
                    Color(0xFF4C0519)
                )
            } else {
                listOf(
                    Color(0xFFFED7AA),
                    Color(0xFFFB923C),
                    Color(0xFFF97316),
                    Color(0xFFF43F5E)
                )
            }
            GradientTheme.OCEAN_BREEZE -> if (isDark) {
                listOf(
                    Color(0xFF06B6D4),
                    Color(0xFF0891B2),
                    Color(0xFF0E7490),
                    Color(0xFF144552)
                )
            } else {
                listOf(
                    Color(0xFF67E8F9),
                    Color(0xFF22D3EE),
                    Color(0xFF06B6D4),
                    Color(0xFF0891B2)
                )
            }
            GradientTheme.MIDNIGHT_DREAM -> if (isDark) {
                listOf(
                    Color(0xFF1E1B4B),
                    Color(0xFF312E81),
                    Color(0xFF4C1D95),
                    Color(0xFF581C87)
                )
            } else {
                listOf(
                    Color(0xFF818CF8),
                    Color(0xFF6366F1),
                    Color(0xFF8B5CF6),
                    Color(0xFF7C3AED)
                )
            }
            GradientTheme.CHERRY_BLOSSOM -> if (isDark) {
                listOf(
                    Color(0xFFDB2777),
                    Color(0xFF9F1239),
                    Color(0xFF881337),
                    Color(0xFF4C0519)
                )
            } else {
                listOf(
                    Color(0xFFFCE7F3),
                    Color(0xFFFBCFE8),
                    Color(0xFFF9A8D4),
                    Color(0xFFF472B6)
                )
            }
            GradientTheme.FOREST_MIST -> if (isDark) {
                listOf(
                    Color(0xFF059669),
                    Color(0xFF047857),
                    Color(0xFF065F46),
                    Color(0xFF064E3B)
                )
            } else {
                listOf(
                    Color(0xFF6EE7B7),
                    Color(0xFF34D399),
                    Color(0xFF10B981),
                    Color(0xFF059669)
                )
            }
            GradientTheme.COSMIC_PURPLE -> if (isDark) {
                listOf(
                    Color(0xFF7C3AED),
                    Color(0xFF6D28D9),
                    Color(0xFF5B21B6),
                    Color(0xFF4C1D95)
                )
            } else {
                listOf(
                    Color(0xFFC084FC),
                    Color(0xFFA855F7),
                    Color(0xFF9333EA),
                    Color(0xFF7E22CE)
                )
            }
            GradientTheme.NORTHERN_LIGHTS -> if (isDark) {
                listOf(
                    Color(0xFF10B981),
                    Color(0xFF14B8A6),
                    Color(0xFF0891B2),
                    Color(0xFF0E7490)
                )
            } else {
                listOf(
                    Color(0xFF6EE7B7),
                    Color(0xFF5EEAD4),
                    Color(0xFF67E8F9),
                    Color(0xFF22D3EE)
                )
            }
            GradientTheme.VELVET_ROSE -> if (isDark) {
                listOf(
                    Color(0xFFBE185D),
                    Color(0xFF9F1239),
                    Color(0xFF881337),
                    Color(0xFF4C0519)
                )
            } else {
                listOf(
                    Color(0xFFFDA4AF),
                    Color(0xFFFB7185),
                    Color(0xFFF43F5E),
                    Color(0xFFE11D48)
                )
            }
            GradientTheme.TROPICAL_PARADISE -> if (isDark) {
                listOf(
                    Color(0xFF14B8A6),
                    Color(0xFF0D9488),
                    Color(0xFFD97706),
                    Color(0xFFB45309)
                )
            } else {
                listOf(
                    Color(0xFF5EEAD4),
                    Color(0xFF2DD4BF),
                    Color(0xFFFBBF24),
                    Color(0xFFF59E0B)
                )
            }
            GradientTheme.SAKURA_DREAM -> if (isDark) {
                listOf(
                    Color(0xFFEC4899),
                    Color(0xFFDB2777),
                    Color(0xFFA855F7),
                    Color(0xFF9333EA)
                )
            } else {
                listOf(
                    Color(0xFFFCE7F3),
                    Color(0xFFFAD5E7),
                    Color(0xFFF9A8D4),
                    Color(0xFFF472B6)
                )
            }
            GradientTheme.NONE -> null
        }
    }
    
    /**
     * Check if gradient should be applied 
     * Gradients are overridden by dynamic album colors
     */
    fun shouldApplyGradient(
        gradientTheme: GradientTheme,
        hasDynamicColors: Boolean,
        dynamicColorsEnabled: Boolean
    ): Boolean {
        // Dynamic album colors take priority
        if (dynamicColorsEnabled && hasDynamicColors) {
            return false
        }
        return gradientTheme != GradientTheme.NONE
    }
}
