package com.aura.music.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Top-level DataStore extension (MUST be at top level per Android documentation)
private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "theme_preferences"
)

/**
 * DataStore based preferences for theme settings
 * Uses encrypted SharedPreferences alternative (DataStore)
 */
class ThemePreferences(private val context: Context) {

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val ACCENT_COLOR_KEY = stringPreferencesKey("accent_color")
        private val GRADIENT_THEME_KEY = stringPreferencesKey("gradient_theme")
        private val DYNAMIC_ALBUM_COLORS_KEY = booleanPreferencesKey("dynamic_album_colors")
        private val MINIMAL_MODE_KEY = booleanPreferencesKey("minimal_mode")
    }

    private val dataStore = context.themeDataStore

    /**
     * Observe theme mode
     */
    fun observeThemeMode(): Flow<ThemeMode> {
        return dataStore.data.map { preferences ->
            val themeModeStr = preferences[THEME_MODE_KEY] ?: ThemeMode.DARK.name
            try {
                ThemeMode.valueOf(themeModeStr)
            } catch (e: Exception) {
                ThemeMode.DARK
            }
        }
    }

    /**
     * Observe accent color
     */
    fun observeAccentColor(): Flow<AccentColor> {
        return dataStore.data.map { preferences ->
            val accentIdStr = preferences[ACCENT_COLOR_KEY] ?: AccentColor.BLUE.id
            AccentColor.fromId(accentIdStr)
        }
    }

    /**
     * Observe gradient theme
     */
    fun observeGradientTheme(): Flow<GradientTheme> {
        return dataStore.data.map { preferences ->
            val gradientIdStr = preferences[GRADIENT_THEME_KEY] ?: GradientTheme.NONE.id
            GradientTheme.fromId(gradientIdStr)
        }
    }

    /**
     * Observe dynamic album colors
     */
    fun observeDynamicAlbumColors(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[DYNAMIC_ALBUM_COLORS_KEY] ?: true
        }
    }

    /**
     * Observe minimal mode
     */
    fun observeMinimalMode(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[MINIMAL_MODE_KEY] ?: false
        }
    }

    /**
     * Save theme mode
     */
    suspend fun saveThemeMode(themeMode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = themeMode.name
        }
    }

    /**
     * Save accent color
     */
    suspend fun saveAccentColor(accentColor: AccentColor) {
        dataStore.edit { preferences ->
            preferences[ACCENT_COLOR_KEY] = accentColor.id
        }
    }

    /**
     * Save gradient theme
     */
    suspend fun saveGradientTheme(gradientTheme: GradientTheme) {
        dataStore.edit { preferences ->
            preferences[GRADIENT_THEME_KEY] = gradientTheme.id
        }
    }

    /**
     * Save dynamic album colors setting
     */
    suspend fun saveDynamicAlbumColors(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DYNAMIC_ALBUM_COLORS_KEY] = enabled
        }
    }

    /**
     * Save minimal mode setting
     */
    suspend fun saveMinimalMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[MINIMAL_MODE_KEY] = enabled
        }
    }

    /**
     * Save all theme settings at once
     */
    suspend fun saveThemeState(themeState: ThemeState) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = themeState.themeMode.name
            preferences[ACCENT_COLOR_KEY] = themeState.accentColor.id
            preferences[GRADIENT_THEME_KEY] = themeState.gradientTheme.id
            preferences[DYNAMIC_ALBUM_COLORS_KEY] = themeState.dynamicAlbumColors
            preferences[MINIMAL_MODE_KEY] = themeState.minimalMode
        }
    }

    /**
     * Clear all theme preferences
     */
    suspend fun clearPreferences() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
