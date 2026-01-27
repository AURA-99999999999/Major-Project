package com.aura.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.model.Playlist
import com.aura.music.data.model.Song
import com.aura.music.data.repository.MusicRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
            _uiState.update { it.copy(isLoading = true, error = null) }

            val trendingDeferred = async { repository.getTrending(20) }
            val playlistsDeferred = async { repository.getPlaylists(userId) }

            val trendingResult = trendingDeferred.await()
            val playlistsResult = playlistsDeferred.await()

            val errorMessage = listOfNotNull(
                trendingResult.exceptionOrNull()?.message,
                playlistsResult.exceptionOrNull()?.message
            ).joinToString(separator = "\n").ifBlank { null }

            _uiState.update {
                it.copy(
                    trendingSongs = trendingResult.getOrElse { emptyList() },
                    playlists = playlistsResult.getOrElse { emptyList() },
                    isLoading = false,
                    error = errorMessage
                )
            }
        }
    }
}

