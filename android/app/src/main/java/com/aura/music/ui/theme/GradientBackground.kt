package com.aura.music.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * A composable that applies a gradient background or solid color based on theme state.
 * 
 * Priority order:
 * 1. Dynamic album colors (if enabled and available)
 * 2. Gradient theme (if selected)
 * 3. Default theme background
 * 
 * @param gradientTheme The gradient theme to apply (NONE, AURORA_GLOW, SUNSET_VIBES)
 * @param isDark Whether dark theme is active
 * @param hasDynamicColors Whether dynamic album colors are active
 * @param dynamicColorsEnabled Whether dynamic colors feature is enabled
 * @param modifier Optional modifier for the background container
 * @param content The composable content to display on top of the background
 */
@Composable
fun GradientBackground(
    gradientTheme: GradientTheme = GradientTheme.NONE,
    isDark: Boolean = true,
    hasDynamicColors: Boolean = false,
    dynamicColorsEnabled: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    // Determine if gradient should be applied
    val shouldApplyGradient = GradientProvider.shouldApplyGradient(
        gradientTheme = gradientTheme,
        hasDynamicColors = hasDynamicColors,
        dynamicColorsEnabled = dynamicColorsEnabled
    )
    
    // Get gradient or use solid color
    val backgroundModifier = if (shouldApplyGradient) {
        val gradient = GradientProvider.getGradient(gradientTheme, isDark)
        if (gradient != null) {
            Modifier.background(brush = gradient)
        } else {
            Modifier.background(MaterialTheme.colorScheme.background)
        }
    } else {
        Modifier.background(MaterialTheme.colorScheme.background)
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(backgroundModifier)
    ) {
        content()
    }
}

/**
 * A simpler gradient background variant for screens that always want gradients when selected.
 * This ignores dynamic colors and applies gradient directly if theme is not NONE.
 * 
 * @param gradientTheme The gradient theme to apply
 * @param isDark Whether dark theme is active
 * @param modifier Optional modifier for the background container
 * @param content The composable content to display on top of the background
 */
@Composable
fun ForceGradientBackground(
    gradientTheme: GradientTheme = GradientTheme.NONE,
    isDark: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val backgroundModifier = if (gradientTheme != GradientTheme.NONE) {
        val gradient = GradientProvider.getGradient(gradientTheme, isDark)
        if (gradient != null) {
            Modifier.background(brush = gradient)
        } else {
            Modifier.background(MaterialTheme.colorScheme.background)
        }
    } else {
        Modifier.background(MaterialTheme.colorScheme.background)
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(backgroundModifier)
    ) {
        content()
    }
}

/**
 * Helper function to create a custom gradient background with specific colors.
 * Useful for screens that want their own unique gradient.
 * 
 * @param colors List of colors for the gradient (vertical)
 * @param modifier Optional modifier for the background container
 * @param content The composable content to display on top of the background
 */
@Composable
fun CustomGradientBackground(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val gradient = Brush.verticalGradient(colors)
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush = gradient)
    ) {
        content()
    }
}
