package com.aura.music.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Predefined accent color palettes for the app
 * Each palette contains primary, secondary, and supporting colors
 */
object AccentPaletteProvider {

    /**
     * Get accent palette by color
     */
    fun getPalette(accent: AccentColor): AccentPaletteColors {
        return when (accent) {
            AccentColor.PURPLE -> purplePalette()
            AccentColor.BLUE -> bluePalette()
            AccentColor.GREEN -> greenPalette()
            AccentColor.ROSE -> rosePalette()
            AccentColor.AMBER -> amberPalette()
            AccentColor.CYAN -> cyanPalette()
            AccentColor.INDIGO -> indigoPalette()
            
            // New aesthetic colors
            AccentColor.OCEAN -> oceanPalette()
            AccentColor.CORAL -> coralPalette()
            AccentColor.LAVENDER -> lavenderPalette()
            AccentColor.MINT -> mintPalette()
            AccentColor.PEACH -> peachPalette()
            AccentColor.CRIMSON -> crimsonPalette()
            AccentColor.TEAL -> tealPalette()
            AccentColor.EMERALD -> emeraldPalette()
            AccentColor.MAGENTA -> magentaPalette()
            AccentColor.SKY -> skyPalette()
            AccentColor.SUNSET -> sunsetPalette()
            AccentColor.ORCHID -> orchidPalette()
            AccentColor.TURQUOISE -> turquoisePalette()
            AccentColor.CHERRY -> cherryPalette()
        }
    }

    private fun purplePalette(): AccentPaletteColors {
        return AccentPaletteColors(
            primary = Color(0xFF9C27B0),
            secondary = Color(0xFFE1BEE7),
            tertiary = Color(0xFFCE93D8),
            light = Color(0xFFF3E5F5),
            dark = Color(0xFF4A148C)
        )
    }

    private fun bluePalette(): AccentPaletteColors {
        return AccentPaletteColors(
            primary = Color(0xFF2196F3),
            secondary = Color(0xFFBBDEFB),
            tertiary = Color(0xFF64B5F6),
            light = Color(0xFFE3F2FD),
            dark = Color(0xFF0D47A1)
        )
    }

    private fun greenPalette(): AccentPaletteColors {
        return AccentPaletteColors(
            primary = Color(0xFF4CAF50),
            secondary = Color(0xFFC8E6C9),
            tertiary = Color(0xFF81C784),
            light = Color(0xFFE8F5E9),
            dark = Color(0xFF1B5E20)
        )
    }

    private fun rosePalette(): AccentPaletteColors {
        return AccentPaletteColors(
            primary = Color(0xFFE91E63),
            secondary = Color(0xFFF8BBD0),
            tertiary = Color(0xFFF48FB1),
            light = Color(0xFFFCE4EC),
            dark = Color(0xFF880E4F)
        )
    }

    private fun amberPalette(): AccentPaletteColors {
        return AccentPaletteColors(
            primary = Color(0xFFFFC107),
            secondary = Color(0xFFFFECB3),
            tertiary = Color(0xFFFFD54F),
            light = Color(0xFFFFF8E1),
            dark = Color(0xFFF57F17)
        )
    }

    private fun cyanPalette(): AccentPaletteColors {
        return AccentPaletteColors(
            primary = Color(0xFF00BCD4),
            secondary = Color(0xFFB2EBF2),
            tertiary = Color(0xFF4DD0E1),
            light = Color(0xFFE0F2F1),
            dark = Color(0xFF006064)
        )
    }

    private fun indigoPalette(): AccentPaletteColors {
        return AccentPaletteColors(
            primary = Color(0xFF3F51B5),
            secondary = Color(0xFFC5CAE9),
            tertiary = Color(0xFF7986CB),
            light = Color(0xFFE8EAF6),
            dark = Color(0xFF1A237E)
        )
    }

    // New aesthetic color palettes
    
    private fun oceanPalette(): AccentPaletteColors {
        return AccentPaletteColors(
            primary = Color(0xFF0077BE),
            secondary = Color(0xFF87CEEB),
            tertiary = Color(0xFF4682B4),
            light = Color(0xFFE0F2F7),
            dark = Color(0xFF004A7C)
        )
    }

    private fun coralPalette(): AccentPaletteColors {
        return AccentPaletteColors(
            primary = Color(0xFFFF6F61),
            secondary = Color(0xFFFFB4A9),
            tertiary = Color(0xFFFF9B8A),
            light = Color(0xFFFFE8E5),
            dark = Color(0xFFCC4D41)
        )
    }

    private fun lavenderPalette(): AccentPaletteColors {
        return AccentPaletteColors(
            primary = Color(0xFF9B7EBD),
            secondary = Color(0xFFD7C9E8),
            tertiary = Color(0xFFB898D4),
            light = Color(0xFFF3EDF7),
            dark = Color(0xFF6D5885)
        )
    }

    private fun mintPalette(): AccentPaletteColors {
        return AccentPaletteColors(
            primary = Color(0xFF3EB489),
            secondary = Color(0xFFA8E6CF),
            tertiary = Color(0xFF66CDAA),
            light = Color(0xFFE8F8F0),
            dark = Color(0xFF2A7A5E)
        )
    }

    private fun peachPalette(): AccentPaletteColors {
        return AccentPaletteColors(
            primary = Color(0xFFFFB07C),
            secondary = Color(0xFFFFD4B3),
            tertiary = Color(0xFFFFBE98),
            light = Color(0xFFFFF5EE),
            dark = Color(0xFFD98555)
        )
    }

    private fun crimsonPalette(): AccentPaletteColors {
        return AccentPaletteColors(
            primary = Color(0xFFDC143C),
            secondary = Color(0xFFFFB6C1),
            tertiary = Color(0xFFE74C6C),
            light = Color(0xFFFFE4E9),
            dark = Color(0xFFA01027)
        )
    }

    private fun tealPalette(): AccentPaletteColors {
        return AccentPaletteColors(
            primary = Color(0xFF008080),
            secondary = Color(0xFF99D6D6),
            tertiary = Color(0xFF4DA6A6),
            light = Color(0xFFE0F2F2),
            dark = Color(0xFF004D4D)
        )
    }

    private fun emeraldPalette(): AccentPaletteColors {
        return AccentPaletteColors(
            primary = Color(0xFF50C878),
            secondary = Color(0xFFA8E4BD),
            tertiary = Color(0xFF7AD99B),
            light = Color(0xFFE8F7ED),
            dark = Color(0xFF339955)
        )
    }

    private fun magentaPalette(): AccentPaletteColors {
        return AccentPaletteColors(
            primary = Color(0xFFFF00FF),
            secondary = Color(0xFFFFB3FF),
            tertiary = Color(0xFFFF66FF),
            light = Color(0xFFFFE6FF),
            dark = Color(0xFFB300B3)
        )
    }

    private fun skyPalette(): AccentPaletteColors {
        return AccentPaletteColors(
            primary = Color(0xFF00BFFF),
            secondary = Color(0xFF87CEFA),
            tertiary = Color(0xFF4DC3FF),
            light = Color(0xFFE0F4FF),
            dark = Color(0xFF0080BF)
        )
    }

    private fun sunsetPalette(): AccentPaletteColors {
        return AccentPaletteColors(
            primary = Color(0xFFFF7F50),
            secondary = Color(0xFFFFB380),
            tertiary = Color(0xFFFF9966),
            light = Color(0xFFFFEEE6),
            dark = Color(0xFFCC5533)
        )
    }

    private fun orchidPalette(): AccentPaletteColors {
        return AccentPaletteColors(
            primary = Color(0xFFDA70D6),
            secondary = Color(0xFFF0C4EE),
            tertiary = Color(0xFFE89DE1),
            light = Color(0xFFFCF0FB),
            dark = Color(0xFFAB4AA2)
        )
    }

    private fun turquoisePalette(): AccentPaletteColors {
        return AccentPaletteColors(
            primary = Color(0xFF40E0D0),
            secondary = Color(0xFF9FF0E8),
            tertiary = Color(0xFF70E8DC),
            light = Color(0xFFE6FAF8),
            dark = Color(0xFF2BAA9F)
        )
    }

    private fun cherryPalette(): AccentPaletteColors {
        return AccentPaletteColors(
            primary = Color(0xFFDE3163),
            secondary = Color(0xFFFFB3C1),
            tertiary = Color(0xFFE96584),
            light = Color(0xFFFFE8ED),
            dark = Color(0xFFA8243F)
        )
    }

    /**
     * Get all available accents
     */
    fun getAllAccents(): List<AccentColor> = AccentColor.entries
}
