package com.aura.music.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Apply gradient background based on theme settings
 * Uses dynamic colors if available and enabled
 */
@Composable
fun DynamicGradientBackground(
    modifier: Modifier = Modifier,
    themeColors: ThemeColors,
    gradientBrush: GradientBrush,
    minimalMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val backgroundColor = when {
        minimalMode -> MaterialTheme.colorScheme.background
        themeColors.vibrant != null && gradientBrush.startColor != Color.Transparent -> {
            // Use vibrant color for gradient
            themeColors.vibrant
        }
        else -> MaterialTheme.colorScheme.background
    }

    val modifier2 = if (gradientBrush.startColor != Color.Transparent && !minimalMode) {
        modifier.background(
            brush = Brush.linearGradient(
                colors = listOf(
                    gradientBrush.startColor.copy(alpha = 0.8f),
                    gradientBrush.endColor.copy(alpha = 0.6f)
                )
            )
        )
    } else {
        modifier.background(backgroundColor)
    }

    Box(
        modifier = modifier2,
        content = { content() }
    )
}

/**
 * Themed surface with appropriate elevation and colors
 */
@Composable
fun ThemedSurface(
    modifier: Modifier = Modifier,
    dynamicColor: Color? = null,
    useAccent: Boolean = false,
    content: @Composable () -> Unit
) {
    val backgroundColor = dynamicColor
        ?: if (useAccent) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        content()
    }
}

/**
 * Themed button with dynamic accent color support
 */
@Composable
fun getThemedButtonColor(
    accentColor: Color = MaterialTheme.colorScheme.primary
): Color {
    return accentColor
}

/**
 * Get contrast-appropriate text color for a background
 */
fun getContrastTextColor(backgroundColor: Color): Color {
    // Simple luminance calculation
    val luminance = (0.299 * backgroundColor.red + 0.587 * backgroundColor.green + 0.114 * backgroundColor.blue)
    return if (luminance > 0.5) Color.Black else Color.White
}
