package com.aura.music.ui.theme

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Central theme manager for app-wide theme state management
 * Handles all theme updates, persistence, and dynamic color extraction
 *
 * Responsibilities:
 * - Manage theme state (StateFlow)
 * - Persist/restore user preferences via DataStore
 * - Extract and apply dynamic album colors
 * - Provide reactive theme updates without activity restart
 * 
 * Thread Safety:
 * - All state updates happen on viewModelScope (Main thread by default)
 * - DataStore operations are coroutine-safe
 * - Album color extraction happens on IO dispatcher
 */
class ThemeManager(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ThemeManager"
    }

    private val themePreferences = ThemePreferences(application)
    private var lastExtractedVideoId: String? = null  // Cache key to avoid re-extraction

    // Private state flows
    private val _themeState = MutableStateFlow(ThemeState())
    private val _isSystemDarkMode = MutableStateFlow(false)

    // Public read-only state flows
    val themeState: StateFlow<ThemeState> = _themeState.asStateFlow()
    val isSystemDarkMode: StateFlow<Boolean> = _isSystemDarkMode.asStateFlow()

    init {
        Log.d(TAG, "✓ ThemeManager created with Application context")
        
        // Load persisted preferences and combine all theme flows
        viewModelScope.launch {
            try {
                combine(
                    themePreferences.observeThemeMode(),
                    themePreferences.observeAccentColor(),
                    themePreferences.observeGradientTheme(),
                    themePreferences.observeDynamicAlbumColors(),
                    themePreferences.observeMinimalMode()
                ) { themeMode, accentColor, gradientTheme, dynamicAlbumColors, minimalMode ->
                    ThemeState(
                        themeMode = themeMode,
                        accentColor = accentColor,
                        gradientTheme = gradientTheme,
                        dynamicAlbumColors = dynamicAlbumColors,
                        minimalMode = minimalMode,
                        currentDynamicColors = _themeState.value.currentDynamicColors
                    )
                }.collect { newState ->
                    _themeState.value = newState
                    Log.d(TAG, "✓ Theme state updated: mode=${newState.themeMode}, accent=${newState.accentColor}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "✗ Failed to load theme preferences", e)
                // Provide sensible defaults
                _themeState.value = ThemeState()
            }
        }
    }

    /**
     * Check if current theme is effectively dark
     * Considers system preference for SYSTEM mode
     */
    fun isDarkTheme(): Boolean {
        return when (_themeState.value.themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> _isSystemDarkMode.value
        }
    }

    /**
     * Update theme mode
     */
    fun setThemeMode(themeMode: ThemeMode) {
        Log.d(TAG, "→ Setting theme mode to: $themeMode")
        viewModelScope.launch {
            try {
                themePreferences.saveThemeMode(themeMode)
                Log.d(TAG, "✓ Theme mode saved: $themeMode")
            } catch (e: Exception) {
                Log.e(TAG, "✗ Failed to save theme mode", e)
            }
        }
    }

    /**
     * Update accent color
     */
    fun setAccentColor(accentColor: AccentColor) {
        Log.d(TAG, "→ Setting accent color to: $accentColor")
        viewModelScope.launch {
            try {
                themePreferences.saveAccentColor(accentColor)
                Log.d(TAG, "✓ Accent color saved: $accentColor")
            } catch (e: Exception) {
                Log.e(TAG, "✗ Failed to save accent color", e)
            }
        }
    }

    /**
     * Update gradient theme
     */
    fun setGradientTheme(gradientTheme: GradientTheme) {
        Log.d(TAG, "→ Setting gradient theme to: $gradientTheme")
        viewModelScope.launch {
            try {
                themePreferences.saveGradientTheme(gradientTheme)
                Log.d(TAG, "✓ Gradient theme saved: $gradientTheme")
            } catch (e: Exception) {
                Log.e(TAG, "✗ Failed to save gradient theme", e)
            }
        }
    }

    /**
     * Toggle dynamic album colors
     */
    fun setDynamicAlbumColors(enabled: Boolean) {
        Log.d(TAG, "→ Setting dynamic album colors to: $enabled")
        viewModelScope.launch {
            try {
                themePreferences.saveDynamicAlbumColors(enabled)
                Log.d(TAG, "✓ Dynamic album colors setting saved: $enabled")
                if (!enabled) {
                    clearDynamicAlbumColors()
                }
            } catch (e: Exception) {
                Log.e(TAG, "✗ Failed to save dynamic album colors setting", e)
            }
        }
    }

    /**
     * Toggle minimal mode
     */
    fun setMinimalMode(enabled: Boolean) {
        Log.d(TAG, "→ Setting minimal mode to: $enabled")
        viewModelScope.launch {
            try {
                themePreferences.saveMinimalMode(enabled)
                Log.d(TAG, "✓ Minimal mode saved: $enabled")
            } catch (e: Exception) {
                Log.e(TAG, "✗ Failed to save minimal mode", e)
            }
        }
    }

    /**
     * Update theme mode from system
     */
    fun updateSystemDarkMode(isDark: Boolean) {
        _isSystemDarkMode.value = isDark
    }

    /**
     * Extract and apply dynamic colors from album artwork
     * Automatically applies if dynamic album colors is enabled
     * Avoids re-extraction for same artwork (uses cache key)
     *
     * @param bitmap Album artwork bitmap
     * @param cacheKey Optional cache key (e.g., videoId to avoid re-extraction)
     */
    fun updateDynamicAlbumColors(bitmap: Bitmap, cacheKey: String? = null) {
        // Skip if extracting for same video
        if (cacheKey != null && cacheKey == lastExtractedVideoId) {
            Log.d(TAG, "⊘ Skipping color extraction for same videoId: $cacheKey")
            return
        }

        if (!_themeState.value.dynamicAlbumColors) {
            Log.d(TAG, "⊘ Dynamic album colors disabled, skipping extraction")
            return
        }

        Log.d(TAG, "→ Extracting colors from artwork (cacheKey=$cacheKey)")
        lastExtractedVideoId = cacheKey
        
        viewModelScope.launch {
            try {
                val colors = AlbumColorExtractor.extractThemeColors(bitmap, cacheKey)
                _themeState.value = _themeState.value.copy(currentDynamicColors = colors)
                Log.d(TAG, "✓ Colors extracted: vibrant=${colors.vibrant}, dominant=${colors.dominant}")
            } catch (e: Exception) {
                Log.e(TAG, "✗ Failed to extract colors", e)
                // Clear dynamic colors on error
                clearDynamicAlbumColors()
            }
        }
    }

    /**
     * Clear dynamic album colors (e.g., when stopping playback)
     */
    fun clearDynamicAlbumColors() {
        Log.d(TAG, "↻ Clearing dynamic album colors")
        lastExtractedVideoId = null
        _themeState.value = _themeState.value.copy(currentDynamicColors = ThemeColors())
    }

    /**
     * Get primary color for UI elements
     * Prioritizes dynamic colors if enabled
     */
    fun getPrimaryColor(): androidx.compose.ui.graphics.Color {
        val currentState = _themeState.value

        // If minimal mode, return neutral color
        if (currentState.minimalMode) {
            return androidx.compose.ui.graphics.Color(0xFF808080)
        }

        // If dynamic colors enabled and available, use vibrant color
        if (currentState.dynamicAlbumColors && currentState.currentDynamicColors.vibrant != null) {
            return currentState.currentDynamicColors.vibrant!!
        }

        // Fallback to accent color
        return AccentPaletteProvider.getPalette(currentState.accentColor).primary
    }

    /**
     * Get current accent palette
     */
    fun getAccentPalette(): AccentPaletteColors {
        return AccentPaletteProvider.getPalette(_themeState.value.accentColor)
    }

    /**
     * Get current gradient
     */
    fun getGradient(): GradientBrush {
        val gradient = _themeState.value.gradientTheme
        return if (_themeState.value.minimalMode) {
            GradientManager.getGradient(GradientTheme.NONE)
        } else {
            GradientManager.getGradient(gradient)
        }
    }

    /**
     * Reset to default theme
     */
    fun resetToDefaults() {
        viewModelScope.launch {
            themePreferences.saveThemeState(ThemeState())
        }
    }

    /**
     * Clear all caches
     */
    fun clearCaches() {
        AlbumColorExtractor.clearCache()
    }
}
