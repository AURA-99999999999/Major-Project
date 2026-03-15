package com.aura.music.ui.screens.language

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.repository.LanguagePreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log

class LanguageSelectionViewModel(
    private val repository: LanguagePreferencesRepository
) : ViewModel() {
    
    private val _userLanguages = MutableStateFlow<List<String>>(emptyList())
    val userLanguages: StateFlow<List<String>> = _userLanguages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    /**
     * Fetch user's language preferences
     */
    fun fetchUserLanguages(uid: String, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.getUserLanguages(uid, forceRefresh)
                }
                
                result.fold(
                    onSuccess = { languages ->
                        _userLanguages.value = languages
                        Log.d("LanguageViewModel", "Fetched ${languages.size} languages for user $uid")
                    },
                    onFailure = { exception ->
                        _error.value = exception.message ?: "Failed to fetch language preferences"
                        Log.e("LanguageViewModel", "Error fetching languages", exception)
                    }
                )
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Update user's language preferences
     */
    suspend fun updateLanguages(uid: String, languages: List<String>): Boolean {
        return try {
            val result = withContext(Dispatchers.IO) {
                repository.updateUserLanguages(uid, languages)
            }
            
            result.fold(
                onSuccess = { updatedLanguages ->
                    _userLanguages.value = updatedLanguages
                    Log.d("LanguageViewModel", "Updated languages successfully for user $uid")
                    true
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "Failed to update language preferences"
                    Log.e("LanguageViewModel", "Error updating languages", exception)
                    false
                }
            )
        } catch (e: Exception) {
            _error.value = e.message ?: "Unknown error occurred"
            Log.e("LanguageViewModel", "Error updating languages", e)
            false
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _error.value = null
    }
}
