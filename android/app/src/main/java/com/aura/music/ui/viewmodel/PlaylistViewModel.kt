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

data class PlaylistUiState(
    val playlists: List<Playlist> = emptyList(),
    val currentPlaylist: Playlist? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class PlaylistViewModel(
    private val repository: MusicRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    fun loadPlaylists(userId: String = "default") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getPlaylists(userId)
                .onSuccess { playlists ->
                    _uiState.value = _uiState.value.copy(
                        playlists = playlists,
                        isLoading = false
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load playlists"
                    )
                }
        }
    }

    fun loadPlaylist(playlistId: String, userId: String = "default") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getPlaylist(playlistId, userId)
                .onSuccess { playlist ->
                    _uiState.value = _uiState.value.copy(
                        currentPlaylist = playlist,
                        isLoading = false
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Playlist not found"
                    )
                }
        }
    }

    fun createPlaylist(name: String, description: String?, userId: String, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.createPlaylist(name, description, userId)
                .onSuccess { playlist ->
                    loadPlaylists(userId)
                    onSuccess(playlist.id)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to create playlist"
                    )
                }
        }
    }

    fun updatePlaylist(
        playlistId: String,
        name: String?,
        description: String?,
        userId: String
    ) {
        viewModelScope.launch {
            repository.updatePlaylist(playlistId, name, description, userId)
                .onSuccess {
                    loadPlaylist(playlistId, userId)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Failed to update playlist"
                    )
                }
        }
    }

    fun deletePlaylist(playlistId: String, userId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId, userId)
                .onSuccess {
                    loadPlaylists(userId)
                    onSuccess()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Failed to delete playlist"
                    )
                }
        }
    }

    fun addSongToPlaylist(playlistId: String, song: Song, userId: String) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, song, userId)
                .onSuccess {
                    loadPlaylist(playlistId, userId)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Failed to add song"
                    )
                }
        }
    }

    fun removeSongFromPlaylist(playlistId: String, videoId: String, userId: String) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, videoId, userId)
                .onSuccess {
                    loadPlaylist(playlistId, userId)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Failed to remove song"
                    )
                }
        }
    }
}

