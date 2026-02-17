package com.aura.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.model.MoodCategory
import com.aura.music.data.model.Song
import com.aura.music.data.model.YTMusicPlaylist
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
        val trendingPlaylists: List<YTMusicPlaylist> = emptyList(),
        val moodCategories: List<MoodCategory> = emptyList(),
        val moodPlaylists: List<YTMusicPlaylist> = emptyList(),
        val selectedMoodTitle: String = "",
        val recommendations: List<Song> = emptyList()
    ) : HomeUiState()

    data class Error(val message: String) : HomeUiState()
}

class HomeViewModel(
    private val repository: MusicRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _recommendedSongs = MutableStateFlow<List<Song>>(emptyList())
    val recommendedSongs: StateFlow<List<Song>> = _recommendedSongs.asStateFlow()

    private var hasLoaded = false
    private var hasLoadedRecommendations = false
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
            
            // Load all home data in parallel
            val homeResult = repository.getHomeData()
            val trendingPlaylistsResult = repository.getTrendingPlaylists()
            val moodCategoriesResult = repository.getMoodCategories()
            
            _uiState.value = homeResult.fold(
                onSuccess = { data ->
                    HomeUiState.Success(
                        trending = data.trending,
                        trendingPlaylists = trendingPlaylistsResult.getOrDefault(emptyList()),
                        moodCategories = moodCategoriesResult.getOrDefault(emptyList()),
                        moodPlaylists = emptyList(),
                        selectedMoodTitle = "",
                        recommendations = data.recommendations
                    )
                },
                onFailure = { error ->
                    HomeUiState.Error(error.message ?: "Failed to load home data")
                }
            )
        }
    }

    fun loadRecommendationsIfNeeded() {
        if (hasLoadedRecommendations) return
        hasLoadedRecommendations = true

        viewModelScope.launch {
            val songs = repository.fetchUserRecommendations()
            _recommendedSongs.value = songs
        }
    }

    fun clearRecommendations() {
        _recommendedSongs.value = emptyList()
        hasLoadedRecommendations = false
    }

    fun selectMood(category: MoodCategory) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is HomeUiState.Success) return@launch
            
            // Load mood playlists for selected category
            val result = repository.getMoodPlaylists(category.params)
            result.fold(
                onSuccess = { playlists ->
                    _uiState.value = currentState.copy(
                        moodPlaylists = playlists,
                        selectedMoodTitle = category.title
                    )
                },
                onFailure = { _ ->
                    // Keep current state but show error somehow
                    _uiState.value = currentState.copy(
                        moodPlaylists = emptyList(),
                        selectedMoodTitle = ""
                    )
                }
            )
        }
    }

    fun clearMoodSelection() {
        val currentState = _uiState.value
        if (currentState is HomeUiState.Success) {
            _uiState.value = currentState.copy(
                moodPlaylists = emptyList(),
                selectedMoodTitle = ""
            )
        }
    }

    private fun findSongByVideoId(videoId: String): Song? {
        val state = _uiState.value
        if (state is HomeUiState.Success) {
            return (state.trending + state.recommendations)
                .firstOrNull { it.videoId == videoId }
        }
        val recommended = _recommendedSongs.value
        if (recommended.isNotEmpty()) {
            return recommended.firstOrNull { it.videoId == videoId }
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

