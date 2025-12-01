package com.aura.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.model.Song
import com.aura.music.data.model.User
import com.aura.music.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: User? = null,
    val likedSongs: List<Song> = emptyList(),
    val recentlyPlayed: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class ProfileViewModel(
    private val repository: MusicRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun loadUserData(userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            repository.getUser(userId)
                .onSuccess { user ->
                    _uiState.value = _uiState.value.copy(user = user)
                }

            repository.getLikedSongs(userId)
                .onSuccess { songs ->
                    _uiState.value = _uiState.value.copy(likedSongs = songs)
                }

            repository.getRecentlyPlayed(userId)
                .onSuccess { songs ->
                    _uiState.value = _uiState.value.copy(recentlyPlayed = songs)
                }

            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun addLikedSong(userId: String, song: Song) {
        viewModelScope.launch {
            repository.addLikedSong(userId, song)
                .onSuccess {
                    loadUserData(userId)
                }
        }
    }

    fun removeLikedSong(userId: String, videoId: String) {
        viewModelScope.launch {
            repository.removeLikedSong(userId, videoId)
                .onSuccess {
                    loadUserData(userId)
                }
        }
    }

    fun isSongLiked(videoId: String): Boolean {
        return _uiState.value.likedSongs.any { it.videoId == videoId }
    }
}

