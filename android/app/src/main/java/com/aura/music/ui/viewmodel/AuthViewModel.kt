package com.aura.music.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.AuraApplication
import com.aura.music.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null
)

class AuthViewModel(
    application: Application,
    private val repository: MusicRepository
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.login(email, password)
                .onSuccess { user ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true
                    )
                    setLoggedIn(true)
                    onSuccess(user.id)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Login failed"
                    )
                }
        }
    }

    fun register(username: String, email: String, password: String, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.register(username, email, password)
                .onSuccess { user ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true
                    )
                    setLoggedIn(true)
                    onSuccess(user.id)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Registration failed"
                    )
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun setLoggedIn(isLoggedIn: Boolean) {
        val authPrefs = (getApplication<AuraApplication>()).authPreferences
        viewModelScope.launch {
            authPrefs.setLoggedIn(isLoggedIn)
        }
    }
}

