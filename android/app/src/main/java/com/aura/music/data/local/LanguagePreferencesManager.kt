package com.aura.music.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Top-level DataStore extension
private val Context.languageDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "language_preferences"
)

/**
 * DataStore based preferences for language settings
 * 
 * Responsibilities:
 * - Cache user's language preferences locally
 * - Reduce API calls by using cached data
 * - Provide Flow-based reactive updates
 */
class LanguagePreferencesManager(private val context: Context) {

    companion object {
        private val USER_LANGUAGES_KEY = stringPreferencesKey("user_languages")
        private val LAST_SYNC_TIME_KEY = longPreferencesKey("last_sync_time")
        
        // Cache expiry time (5 minutes in milliseconds)
        private const val CACHE_EXPIRY_MS = 5 * 60 * 1000L
    }

    private val dataStore = context.languageDataStore
    private val gson = Gson()

    /**
     * Observe user's language preferences
     */
    fun observeUserLanguages(): Flow<List<String>> {
        return dataStore.data.map { preferences ->
            val languagesJson = preferences[USER_LANGUAGES_KEY] ?: ""
            if (languagesJson.isNotEmpty()) {
                try {
                    val type = object : TypeToken<List<String>>() {}.type
                    gson.fromJson<List<String>>(languagesJson, type)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }
    }

    /**
     * Get user's language preferences synchronously
     */
    suspend fun getUserLanguages(): List<String> {
        return try {
            val preferences = dataStore.data.first()
            val languagesJson = preferences[USER_LANGUAGES_KEY] ?: ""
            if (languagesJson.isNotEmpty()) {
                try {
                    val type = object : TypeToken<List<String>>() {}.type
                    gson.fromJson(languagesJson, type) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Save user's language preferences
     */
    suspend fun saveUserLanguages(languages: List<String>) {
        dataStore.edit { preferences ->
            val languagesJson = gson.toJson(languages)
            preferences[USER_LANGUAGES_KEY] = languagesJson
            preferences[LAST_SYNC_TIME_KEY] = System.currentTimeMillis()
        }
    }

    /**
     * Check if cached data is still valid
     */
    suspend fun isCacheValid(): Boolean {
        return try {
            val preferences = dataStore.data.first()
            val lastSyncTime = preferences[LAST_SYNC_TIME_KEY] ?: 0L
            val currentTime = System.currentTimeMillis()
            (currentTime - lastSyncTime) < CACHE_EXPIRY_MS
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clear all cached language preferences
     */
    suspend fun clearCache() {
        dataStore.edit { preferences ->
            preferences.remove(USER_LANGUAGES_KEY)
            preferences.remove(LAST_SYNC_TIME_KEY)
        }
    }

    /**
     * Check if user has any language preferences set
     */
    fun hasLanguagePreferences(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            val languagesJson = preferences[USER_LANGUAGES_KEY] ?: ""
            if (languagesJson.isEmpty()) {
                false
            } else {
                try {
                    val type = object : TypeToken<List<String>>() {}.type
                    val languages = gson.fromJson<List<String>>(languagesJson, type) ?: emptyList()
                    languages.isNotEmpty()
                } catch (e: Exception) {
                    false
                }
            }
        }
    }
}
