package com.aura.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.model.Playlist
import com.aura.music.data.model.Song
import com.aura.music.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val trendingSongs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class HomeViewModel(
    private val repository: MusicRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun loadData(userId: String = "default") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            repository.getTrending(20)
                .onSuccess { songs ->
                    _uiState.value = _uiState.value.copy(trendingSongs = songs)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Failed to load trending songs"
                    )
                }

            repository.getPlaylists(userId)
                .onSuccess { playlists ->
                    _uiState.value = _uiState.value.copy(playlists = playlists)
                }
                .onFailure { e ->
                    // Don't show error for playlists as they might not exist
                }

            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
}

