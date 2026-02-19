package com.aura.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.model.MoodCategory
import com.aura.music.data.model.Song
import com.aura.music.data.model.YTMusicPlaylist
import com.aura.music.data.remote.dto.TopArtistDto
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

    private val _topArtists = MutableStateFlow<List<TopArtistDto>>(emptyList())
    val topArtists: StateFlow<List<TopArtistDto>> = _topArtists.asStateFlow()

    private var hasLoaded = false
    private var hasLoadedRecommendations = false
    private var musicService: MusicService? = null

    fun attachMusicService(service: MusicService?) {
        if (service != null) {
            musicService = service
        }
    }

    fun playSongFromList(songs: List<Song>, startIndex: Int, source: String) {
        if (songs.isEmpty()) return
        if (startIndex !in songs.indices) return
        val service = musicService ?: return
        service.setQueueAndPlay(songs, startIndex, source)
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
            
            // Also load top artists alongside recommendations
            loadTopArtists()
        }
    }

    fun loadTopArtists() {
        viewModelScope.launch {
            val result = repository.getTopArtists(limit = 10)
            result.fold(
                onSuccess = { artists ->
                    _topArtists.value = artists
                },
                onFailure = { _ ->
                    _topArtists.value = emptyList()
                }
            )
        }
    }

    fun clearRecommendations() {
        _recommendedSongs.value = emptyList()
        _topArtists.value = emptyList()
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

}

