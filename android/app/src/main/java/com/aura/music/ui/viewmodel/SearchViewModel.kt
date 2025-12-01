package com.aura.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.model.Song
import com.aura.music.data.repository.MusicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class SearchViewModel(
    private val repository: MusicRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun search(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (query.isBlank()) {
                _uiState.value = _uiState.value.copy(results = emptyList(), isLoading = false)
                return@launch
            }

            delay(300) // Debounce
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            repository.searchSongs(query, 50)
                .onSuccess { songs ->
                    _uiState.value = _uiState.value.copy(
                        results = songs,
                        isLoading = false
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Search failed"
                    )
                }
        }
    }

    fun clearSearch() {
        _uiState.value = SearchUiState()
    }
}

