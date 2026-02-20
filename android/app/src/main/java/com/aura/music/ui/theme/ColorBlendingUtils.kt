package com.aura.music.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.pow

/**
 * Color manipulation utilities for blending, darkening, and lightening colors
 * Ensures player background readability and smooth gradient transitions
 */
object ColorBlendingUtils {

    /**
     * Blend two colors with specified alpha weight
     * 
     * @param color1 First color (typically album color)
     * @param color2 Second color (typically theme color)
     * @param alphaBlend Weight of first color (0f-1f), where 1f = 100% color1
     * @return Blended color
     */
    fun blendColors(color1: Color, color2: Color, alphaBlend: Float = 0.7f): Color {
        val alpha = alphaBlend.coerceIn(0f, 1f)
        return Color(
            red = (color1.red * alpha + color2.red * (1f - alpha)).coerceIn(0f, 1f),
            green = (color1.green * alpha + color2.green * (1f - alpha)).coerceIn(0f, 1f),
            blue = (color1.blue * alpha + color2.blue * (1f - alpha)).coerceIn(0f, 1f),
            alpha = 1f
        )
    }

    /**
     * Darken a color by a specified factor
     * Useful for creating gradient depth
     * 
     * @param color Color to darken
     * @param factor Darkening factor (0f-1f), where 0.5f darkens by ~37%
     * @return Darkened color
     */
    fun darken(color: Color, factor: Float = 0.35f): Color {
        val f = (1f - factor).coerceIn(0f, 1f)
        return Color(
            red = (color.red * f).coerceIn(0f, 1f),
            green = (color.green * f).coerceIn(0f, 1f),
            blue = (color.blue * f).coerceIn(0f, 1f),
            alpha = 1f
        )
    }

    /**
     * Lighten a color by a specified factor
     * Useful for gradient highlights
     * 
     * @param color Color to lighten
     * @param factor Lightening factor (0f-1f), where 0.25f lightens by ~25%
     * @return Lightened color
     */
    fun lighten(color: Color, factor: Float = 0.25f): Color {
        val f = factor.coerceIn(0f, 1f)
        return Color(
            red = (color.red + (1f - color.red) * f).coerceIn(0f, 1f),
            green = (color.green + (1f - color.green) * f).coerceIn(0f, 1f),
            blue = (color.blue + (1f - color.blue) * f).coerceIn(0f, 1f),
            alpha = 1f
        )
    }

    /**
     * Saturate a color to make it more vibrant
     * 
     * @param color Color to saturate
     * @param factor Saturation factor (0f-1f), where 0.5f adds 50% more saturation
     * @return Saturated color
     */
    fun saturate(color: Color, factor: Float = 0.2f): Color {
        return adjustHSV(color, saturation = 1f + factor.coerceIn(-1f, 1f))
    }

    /**
     * Desaturate a color to make it more muted
     * 
     * @param color Color to desaturate
     * @param factor Desaturation factor (0f-1f), where 0.3f removes 30% saturation
     * @return Desaturated color
     */
    fun desaturate(color: Color, factor: Float = 0.3f): Color {
        return adjustHSV(color, saturation = (1f - factor).coerceIn(0f, 1f))
    }

    /**
     * Calculate perceived brightness of a color
     * Uses relative luminance formula for accurate perception
     * 
     * @param color Color to measure
     * @return Brightness value (0f-1f)
     */
    fun getPerceivedBrightness(color: Color): Float {
        // Relative luminance formula: ITU-R BT.709
        val r = if (color.red <= 0.03928) color.red / 12.92f else ((color.red + 0.055f) / 1.055f).pow(2.4f)
        val g = if (color.green <= 0.03928) color.green / 12.92f else ((color.green + 0.055f) / 1.055f).pow(2.4f)
        val b = if (color.blue <= 0.03928) color.blue / 12.92f else ((color.blue + 0.055f) / 1.055f).pow(2.4f)
        
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    /**
     * Check if a color is considered "dark" (brightness < 0.5)
     */
    fun isDarkColor(color: Color): Boolean = getPerceivedBrightness(color) < 0.5f

    /**
     * Check if a color is considered "light" (brightness >= 0.5)
     */
    fun isLightColor(color: Color): Boolean = !isDarkColor(color)

    /**
     * Calculate contrast ratio between two colors
     * Useful for accessibility (WCAG)
     * 
     * @param color1 First color
     * @param color2 Second color
     * @return Contrast ratio (1-21)
     */
    fun getContrastRatio(color1: Color, color2: Color): Float {
        val l1 = getPerceivedBrightness(color1) + 0.05f
        val l2 = getPerceivedBrightness(color2) + 0.05f
        
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        
        return lighter / darker
    }

    /**
     * Get an appropriate text color (white or dark gray) for contrast on background
     * 
     * @param backgroundColor Background color to contrast against
     * @return White if background is dark, Dark gray if background is light
     */
    fun getContrastTextColor(backgroundColor: Color): Color {
        return if (isDarkColor(backgroundColor)) {
            Color.White
        } else {
            Color(0xFF1A1A1A)  // Dark gray instead of pure black for less harsh contrast
        }
    }

    /**
     * Adjust HSV values of a color
     * Internal utility for saturation/hue manipulation
     */
    private fun adjustHSV(color: Color, hue: Float = 1f, saturation: Float = 1f, value: Float = 1f): Color {
        val rgb = ((color.red * 255).toInt() shl 16) or
                  ((color.green * 255).toInt() shl 8) or
                  (color.blue * 255).toInt()
        
        val r = ((rgb shr 16) and 0xFF).toFloat()
        val g = ((rgb shr 8) and 0xFF).toFloat()
        val b = (rgb and 0xFF).toFloat()
        
        val cmax = maxOf(r, g, b)
        val cmin = minOf(r, g, b)
        val delta = cmax - cmin
        
        var h = when {
            delta == 0f -> 0f
            cmax == r -> 60f * ((g - b) / delta + if (g < b) 6f else 0f)
            cmax == g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }
        
        var s = if (cmax == 0f) 0f else delta / cmax
        val v = cmax / 255f
        
        // Apply adjustments
        h = (h * hue) % 360f
        s = (s * saturation).coerceIn(0f, 1f)
        val newV = (v * value).coerceIn(0f, 1f)
        
        // Convert back to RGB
        val c = newV * s
        val x = c * (1 - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = newV - c
        
        val (rp, gp, bp) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        
        return Color(rp + m, gp + m, bp + m)
    }
}
