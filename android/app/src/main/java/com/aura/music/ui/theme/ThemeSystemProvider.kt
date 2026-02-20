package com.aura.music.ui.theme

import android.app.Application
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Provides convenient access to theme system throughout the app
 */
object ThemeSystemProvider {

    /**
     * Get theme manager instance in a composable
     */
    @Composable
    fun rememberThemeManager(): ThemeManager {
        return viewModel()
    }

    /**
     * Check if system is in dark mode
     */
    fun isSystemDarkMode(context: android.content.Context): Boolean {
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Get initial theme state based on settings
     */
    fun getInitialThemeState(): ThemeState {
        return ThemeState(
            themeMode = ThemeMode.DARK,
            accentColor = AccentColor.BLUE,
            gradientTheme = GradientTheme.NONE,
            dynamicAlbumColors = true,
            minimalMode = false
        )
    }
}

/**
 * Extension to check system dark mode preference
 */
@Composable
fun isSystemDarkMode(): Boolean {
    val context = LocalContext.current
    return ThemeSystemProvider.isSystemDarkMode(context)
}
