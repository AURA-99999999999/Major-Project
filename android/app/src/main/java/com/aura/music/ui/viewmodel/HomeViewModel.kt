package com.aura.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.model.Song
import com.aura.music.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class HomeUiState {
    object Loading : HomeUiState()

    data class Success(
        val trending: List<Song>,
        val recommendations: List<Song> = emptyList()
    ) : HomeUiState()

    data class Error(val message: String) : HomeUiState()
}

class HomeViewModel(
    private val repository: MusicRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var hasLoaded = false

    fun loadHomeData() {
        if (hasLoaded) return
        hasLoaded = true

        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            val result = repository.getHomeData()
            _uiState.value = result.fold(
                onSuccess = { data ->
                    HomeUiState.Success(
                        trending = data.trending,
                        recommendations = data.recommendations
                    )
                },
                onFailure = { error ->
                    HomeUiState.Error(error.message ?: "Failed to load home data")
                }
            )
        }
    }
}

