package com.aura.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.repository.LanguagePreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LanguagePreferencesViewModel(
    private val repository: LanguagePreferencesRepository
) : ViewModel() {

    private val _languages = MutableStateFlow<List<String>>(emptyList())
    val languages: StateFlow<List<String>> = _languages.asStateFlow()

    private val _hasLanguagePreferences = MutableStateFlow(false)
    val hasLanguagePreferences: StateFlow<Boolean> = _hasLanguagePreferences.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private var loadedUid: String? = null

    init {
        viewModelScope.launch {
            repository.observeUserLanguages().collect { langs ->
                _languages.value = langs
                _hasLanguagePreferences.value = langs.isNotEmpty()
            }
        }
    }

    fun fetchOnLogin(uid: String) {
        if (uid.isBlank()) return
        if (loadedUid == uid && _isLoaded.value) return

        viewModelScope.launch {
            loadedUid = uid
            repository.getUserLanguages(uid = uid, forceRefresh = true)
            _isLoaded.value = true
        }
    }

    fun clearSession() {
        loadedUid = null
        _isLoaded.value = false
        _languages.value = emptyList()
        _hasLanguagePreferences.value = false
    }
}
