package com.aura.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.model.MoodCategory
import com.aura.music.data.model.Song
import com.aura.music.data.model.YTMusicPlaylist
import com.aura.music.data.remote.dto.TopArtistDto
import com.aura.music.data.repository.MusicRepository
import com.aura.music.data.repository.PlaylistRepository
import com.aura.music.player.MusicService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async

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

// Individual section loading states
data class SectionLoadingState(
    val isTrendingLoading: Boolean = true,
    val isRecommendationsLoading: Boolean = true,
    val isTopArtistsLoading: Boolean = true
)

// UI event for mix operations
sealed class MixEvent {
    data class ShowMessage(val message: String) : MixEvent()
    object MixSaved : MixEvent()
}

class HomeViewModel(
    private val repository: MusicRepository,
    private val playlistRepository: PlaylistRepository? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _recommendedSongs = MutableStateFlow<List<Song>>(emptyList())
    val recommendedSongs: StateFlow<List<Song>> = _recommendedSongs.asStateFlow()

    private val _topArtists = MutableStateFlow<List<TopArtistDto>>(emptyList())
    val topArtists: StateFlow<List<TopArtistDto>> = _topArtists.asStateFlow()

    // Individual section loading states for progressive rendering
    private val _sectionLoadingState = MutableStateFlow(SectionLoadingState())
    val sectionLoadingState: StateFlow<SectionLoadingState> = _sectionLoadingState.asStateFlow()

    // Mix operation event feedback
    private val _mixEvents = MutableStateFlow<MixEvent?>(null)
    val mixEvents: StateFlow<MixEvent?> = _mixEvents.asStateFlow()

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
            
            try {
                // Load all home data in PARALLEL using coroutineScope
                coroutineScope {
                    // Launch all API calls concurrently
                    val trendingDeferred = async { repository.getHomeData() }
                    val playlistsDeferred = async { repository.getTrendingPlaylists() }
                    val moodsDeferred = async { repository.getMoodCategories() }
                    
                    // Await all results
                    val homeResult = trendingDeferred.await()
                    val trendingPlaylistsResult = playlistsDeferred.await()
                    val moodCategoriesResult = moodsDeferred.await()
                    
                    // Update UI with trending data immediately (fastest to load)
                    homeResult.fold(
                        onSuccess = { data ->
                            _uiState.value = HomeUiState.Success(
                                trending = data.trending,
                                trendingPlaylists = trendingPlaylistsResult.getOrDefault(emptyList()),
                                moodCategories = moodCategoriesResult.getOrDefault(emptyList()),
                                moodPlaylists = emptyList(),
                                selectedMoodTitle = "",
                                recommendations = data.recommendations
                            )
                            // Mark trending as loaded
                            _sectionLoadingState.value = _sectionLoadingState.value.copy(
                                isTrendingLoading = false
                            )
                        },
                        onFailure = { error ->
                            _uiState.value = HomeUiState.Error(error.message ?: "Failed to load home data")
                            _sectionLoadingState.value = _sectionLoadingState.value.copy(
                                isTrendingLoading = false
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Failed to load home data")
                _sectionLoadingState.value = SectionLoadingState(
                    isTrendingLoading = false,
                    isRecommendationsLoading = _sectionLoadingState.value.isRecommendationsLoading,
                    isTopArtistsLoading = _sectionLoadingState.value.isTopArtistsLoading
                )
            }
        }
    }

    fun loadRecommendationsIfNeeded() {
        if (hasLoadedRecommendations) return
        hasLoadedRecommendations = true

        viewModelScope.launch {
            try {
                // Load recommendations and top artists in PARALLEL
                coroutineScope {
                    val recommendationsDeferred = async { repository.fetchUserRecommendations() }
                    val topArtistsDeferred = async { repository.getTopArtists(limit = 10) }
                    
                    // Await recommendations
                    val songs = recommendationsDeferred.await()
                    _recommendedSongs.value = songs
                    _sectionLoadingState.value = _sectionLoadingState.value.copy(
                        isRecommendationsLoading = false
                    )
                    
                    // Await top artists
                    val artistsResult = topArtistsDeferred.await()
                    artistsResult.fold(
                        onSuccess = { artists ->
                            _topArtists.value = artists
                        },
                        onFailure = { _ ->
                            _topArtists.value = emptyList()
                        }
                    )
                    _sectionLoadingState.value = _sectionLoadingState.value.copy(
                        isTopArtistsLoading = false
                    )
                }
            } catch (e: Exception) {
                // Graceful failure - mark as loaded even on error
                _sectionLoadingState.value = _sectionLoadingState.value.copy(
                    isRecommendationsLoading = false,
                    isTopArtistsLoading = false
                )
            }
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

    /**
     * Shuffles and plays a daily mix
     * - Shuffles the track order
     * - Sets the queue with the shuffled songs
     * - Starts playback immediately
     * - Tracks source as "daily_mix" for analytics
     */
    fun shufflePlayMix(mixKey: String, songs: List<Song>) {
        if (songs.isEmpty()) {
            _mixEvents.value = MixEvent.ShowMessage("No songs in this mix")
            return
        }

        if (musicService == null) {
            _mixEvents.value = MixEvent.ShowMessage("Player not ready")
            return
        }

        // Shuffle the songs
        val shuffledSongs = songs.shuffled()

        // Play with shuffled order
        musicService?.setQueueAndPlay(shuffledSongs, 0, "daily_mix_$mixKey")
        _mixEvents.value = MixEvent.ShowMessage("Shuffle playing ${shuffledSongs.size} songs")
    }

    /**
     * Saves a daily mix to the user's library
     * - Creates a new playlist with the mix name
     * - Adds all songs from the mix to the playlist
     * - Prevents duplicate saves
     * - Shows feedback to user
     */
    fun saveMixToLibrary(mixKey: String, mixName: String, songs: List<Song>) {
        if (songs.isEmpty()) {
            _mixEvents.value = MixEvent.ShowMessage("No songs to save")
            return
        }

        if (playlistRepository == null) {
            _mixEvents.value = MixEvent.ShowMessage("Error: Playlist service unavailable")
            return
        }

        viewModelScope.launch {
            try {
                val result = playlistRepository.saveMixToLibrary(mixKey, mixName, songs)
                result.fold(
                    onSuccess = { playlistId ->
                        _mixEvents.value = MixEvent.ShowMessage("✓ Saved \"$mixName\" to library")
                        _mixEvents.value = MixEvent.MixSaved
                    },
                    onFailure = { e ->
                        val message = e.message ?: "Failed to save mix"
                        _mixEvents.value = MixEvent.ShowMessage(message)
                    }
                )
            } catch (e: Exception) {
                _mixEvents.value = MixEvent.ShowMessage("Error: ${e.message}")
            }
        }
    }

    fun clearMixEvent() {
        _mixEvents.value = null
    }

}

