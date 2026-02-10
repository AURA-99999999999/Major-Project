package com.aura.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.model.Song
import com.aura.music.data.repository.MusicRepository
import com.aura.music.player.MusicService
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
    private var musicService: MusicService? = null

    fun attachMusicService(service: MusicService?) {
        if (service != null) {
            musicService = service
        }
    }

    fun playSongByVideoId(videoId: String) {
        if (videoId.isBlank()) return
        val service = musicService ?: return
        val state = service.playerState.value
        if (state.currentSong?.videoId == videoId && state.isPlaying) return

        viewModelScope.launch {
            val fallbackSong = findSongByVideoId(videoId)
            val resolvedSong = repository.getSong(videoId)
                .getOrNull()
                ?.let { mergeMetadataFromFallback(it, fallbackSong) }

            if (resolvedSong == null || resolvedSong.url.isNullOrBlank()) return@launch
            service.playResolvedSong(resolvedSong, false, "trending")
        }
    }

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

    private fun findSongByVideoId(videoId: String): Song? {
        val state = _uiState.value
        if (state is HomeUiState.Success) {
            return (state.trending + state.recommendations)
                .firstOrNull { it.videoId == videoId }
        }
        return null
    }

    private fun mergeMetadataFromFallback(resolved: Song, fallback: Song?): Song {
        if (fallback == null) return resolved
        return resolved.copy(
            title = if (fallback.title.isNotBlank()) fallback.title else resolved.title,
            artist = fallback.artist ?: resolved.artist,
            artists = if (!fallback.artists.isNullOrEmpty()) fallback.artists else resolved.artists,
            thumbnail = fallback.thumbnail ?: resolved.thumbnail
        )
    }
}

