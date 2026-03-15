package com.aura.music.data.repository

import android.util.Log
import com.aura.music.data.local.LanguagePreferencesManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing language preferences
 * 
 * Responsibilities:
 * - Fetch language preferences from API
 * - Cache preferences locally using DataStore
 * - Provide cached data when available
 * - Update preferences on both API and local cache
 * 
 * Caching Strategy:
 * - Cache expires after 5 minutes
 * - Always use cached data if valid
 * - Refresh from API if cache is expired or empty
 */
class LanguagePreferencesRepository(
    private val preferencesManager: LanguagePreferencesManager,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        private const val TAG = "LanguagePrefsRepo"
    }

    /**
     * Get user's language preferences
     * Uses cache if valid, otherwise fetches from API
     */
    suspend fun getUserLanguages(uid: String, forceRefresh: Boolean = false): Result<List<String>> {
        return try {
            // Check cache first
            if (!forceRefresh) {
                val cachedLanguages = preferencesManager.getUserLanguages()
                val isCacheValid = preferencesManager.isCacheValid()

                if (isCacheValid) {
                    Log.d(TAG, "Returning cached languages for user $uid: $cachedLanguages")
                    return Result.success(cachedLanguages)
                }
            }

            // Fetch from Firestore users/{uid}
            Log.d(TAG, "Fetching languages from Firestore for user $uid")
            val snapshot = firestore.collection("users").document(uid).get().await()
            val languages = when (val value = snapshot.get("languagePreferences")) {
                is List<*> -> value.mapNotNull { it?.toString()?.trim()?.lowercase() }.filter { it.isNotEmpty() }
                else -> emptyList()
            }

            // Update cache (including empty list semantics)
            preferencesManager.saveUserLanguages(languages)
            Log.d(TAG, "Fetched and cached ${languages.size} languages for user $uid")
            Result.success(languages)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching languages for user $uid", e)

            // Try to return cached data as fallback
            val cachedLanguages = preferencesManager.getUserLanguages()
            Log.d(TAG, "Returning cached languages as fallback: $cachedLanguages")
            Result.success(cachedLanguages)
        }
    }

    /**
     * Update user's language preferences
     * Updates both API and local cache
     */
    suspend fun updateUserLanguages(uid: String, languages: List<String>): Result<List<String>> {
        return try {
            val normalizedLanguages = languages
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .distinct()

            if (normalizedLanguages.isEmpty()) {
                return Result.failure(IllegalArgumentException("At least one language must be selected"))
            }

            Log.d(TAG, "Updating Firestore languages for user $uid: $normalizedLanguages")

            firestore.collection("users")
                .document(uid)
                .set(
                    mapOf("languagePreferences" to normalizedLanguages),
                    SetOptions.merge()
                )
                .await()

            preferencesManager.saveUserLanguages(normalizedLanguages)
            Log.d(TAG, "Successfully updated languages for user $uid")
            Result.success(normalizedLanguages)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating languages for user $uid", e)
            Result.failure(e)
        }
    }

    /**
     * Observe user's language preferences as Flow
     */
    fun observeUserLanguages(): Flow<List<String>> {
        return preferencesManager.observeUserLanguages()
    }

    /**
     * Check if user has any language preferences set
     */
    fun hasLanguagePreferences(): Flow<Boolean> {
        return preferencesManager.hasLanguagePreferences()
    }

    /**
     * Clear cached language preferences
     */
    suspend fun clearCache() {
        preferencesManager.clearCache()
        Log.d(TAG, "Cleared language preferences cache")
    }
}
