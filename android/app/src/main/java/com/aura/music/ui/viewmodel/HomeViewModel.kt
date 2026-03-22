package com.aura.music.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.model.MoodCategory
import com.aura.music.data.model.Song
import com.aura.music.data.model.TrendingData
import com.aura.music.data.model.JioSaavnPlaylist
import com.aura.music.data.remote.dto.TopArtistDto
import com.aura.music.data.repository.MusicRepository
import com.aura.music.data.repository.PlaylistRepository
import com.aura.music.data.repository.RecentlyPlayedRepository
import com.aura.music.data.repository.FirestoreRepository
import com.aura.music.data.mapper.toSongs
import com.aura.music.player.MusicService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

sealed class HomeUiState {
    object Loading : HomeUiState()

    data class Success(
        val trendingData: TrendingData = TrendingData(),
        val trending: List<Song> = emptyList(), // Deprecated - kept for backward compatibility
        val trendingPlaylists: List<JioSaavnPlaylist> = emptyList(),
        val moodCategories: List<MoodCategory> = emptyList(),
        val moodPlaylists: List<JioSaavnPlaylist> = emptyList(),
        val selectedMoodTitle: String = "",
        val recommendations: List<Song> = emptyList(),
        val collaborativeRecommendations: List<Song> = emptyList(),
        val collaborativeTitle: String = "Users Like You Also Listen To",
        val collaborative: List<Song> = emptyList()
    ) : HomeUiState()

    data class Error(val message: String) : HomeUiState()
}

// Individual section loading states
data class SectionLoadingState(
    val isTrendingLoading: Boolean = true,
    val isRecommendationsLoading: Boolean = true,
    val isTopArtistsLoading: Boolean = true,
    val isCollaborativeLoading: Boolean = true
)

// UI event for mix operations
sealed class MixEvent {
    data class ShowMessage(val message: String) : MixEvent()
    object MixSaved : MixEvent()
}

class HomeViewModel(
    private val repository: MusicRepository,
    private val recentlyPlayedRepository: RecentlyPlayedRepository,
    private val playlistRepository: PlaylistRepository? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _recommendedSongs = MutableStateFlow<List<Song>>(emptyList())
    val recommendedSongs: StateFlow<List<Song>> = _recommendedSongs.asStateFlow()

    private val _topArtists = MutableStateFlow<List<TopArtistDto>>(emptyList())
    val topArtists: StateFlow<List<TopArtistDto>> = _topArtists.asStateFlow()

    private val _recentlyPlayedSongs = MutableStateFlow<List<Song>>(emptyList())
    val recentlyPlayedSongs: StateFlow<List<Song>> = _recentlyPlayedSongs.asStateFlow()

    // Individual section loading states for progressive rendering
    private val _sectionLoadingState = MutableStateFlow(SectionLoadingState())
    val sectionLoadingState: StateFlow<SectionLoadingState> = _sectionLoadingState.asStateFlow()

    // Pull-to-refresh state
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Mix operation event feedback
    private val _mixEvents = MutableStateFlow<MixEvent?>(null)
    val mixEvents: StateFlow<MixEvent?> = _mixEvents.asStateFlow()

    private var hasLoaded = false
    private var hasLoadedRecommendations = false
    private var hasLoadedCollaborative = false
    private var lastHomeLoadTimestampMs = 0L
    private var lastRecommendationsLoadTimestampMs = 0L
    private var lastCollaborativeLoadTimestampMs = 0L
    private var musicService: MusicService? = null
    private var currentUserId: String? = null
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestoreRepository by lazy { FirestoreRepository() }

    companion object {
        private const val TAG = "HomeViewModel"
        private const val HOME_CACHE_TTL_MS = 5 * 60 * 1000L
        private const val CF_SECTION_TITLE = "Users Like You Also Listen To"
    }

    private suspend fun loadCollaborativeRecommendations(forceRefresh: Boolean = false) {
        if (!forceRefresh && hasLoadedCollaborative && isTtlValid(lastCollaborativeLoadTimestampMs)) {
            _sectionLoadingState.value = _sectionLoadingState.value.copy(isCollaborativeLoading = false)
            return
        }

        hasLoadedCollaborative = true
        _sectionLoadingState.value = _sectionLoadingState.value.copy(isCollaborativeLoading = true)
        Log.d(TAG, "[CF_UI] Starting lazy collaborative recommendations fetch")

        val result = repository.fetchCollaborativeRecommendations(limit = 12)
        result.fold(
            onSuccess = { section ->
                val currentState = _uiState.value
                if (currentState is HomeUiState.Success) {
                    _uiState.value = currentState.copy(
                        collaborativeRecommendations = section.tracks,
                        collaborativeTitle = CF_SECTION_TITLE,
                        collaborative = section.tracks
                    )
                    Log.d(TAG, "[CF_UI] Loaded ${section.tracks.size} collaborative songs")
                }
                lastCollaborativeLoadTimestampMs = System.currentTimeMillis()
            },
            onFailure = { error ->
                Log.w(TAG, "[CF_UI] Failed to load collaborative recommendations: ${error.message}")
                val currentState = _uiState.value
                if (currentState is HomeUiState.Success) {
                    _uiState.value = currentState.copy(
                        collaborativeRecommendations = emptyList(),
                        collaborativeTitle = CF_SECTION_TITLE,
                        collaborative = emptyList()
                    )
                }
            }
        )

        _sectionLoadingState.value = _sectionLoadingState.value.copy(isCollaborativeLoading = false)
    }

    private fun isTtlValid(lastUpdatedMs: Long): Boolean {
        if (lastUpdatedMs <= 0L) return false
        return (System.currentTimeMillis() - lastUpdatedMs) < HOME_CACHE_TTL_MS
    }

    init {
        observeAuthStateChanges()
        observeRecentlyPlayed()
        runApiConnectivityTest()
    }

    private fun runApiConnectivityTest() {
        viewModelScope.launch {
            repeat(2) { attempt ->
                try {
                    val result = repository.getTrending(limit = 1)
                    result
                        .onSuccess { songs ->
                            Log.d(TAG, "API_TEST success attempt=${attempt + 1} count=${songs.size}")
                        }
                        .onFailure { err ->
                            Log.e(TAG, "API_TEST failed attempt=${attempt + 1}: ${err.message}")
                        }
                    return@launch
                } catch (e: Exception) {
                    Log.e(TAG, "API_TEST exception attempt=${attempt + 1}: ${e.message}", e)
                    delay(2000)
                }
            }
        }
    }

    /**
     * Monitor authentication state changes and sync Recently Played data
     * when user logs in/out or switches account.
     * 
     * Flow:
     * 1. User logs in → fetch recently played from Firestore
     * 2. Hydrate local cache with cloud data
     * 3. UI loads from local cache (fast) while Firestore syncs in background
     */
    private fun observeAuthStateChanges() {
        auth.addAuthStateListener { firebaseAuth ->
            val newUserId = firebaseAuth.currentUser?.uid
            
            // User changed (login, logout, or switch account)
            if (newUserId != currentUserId) {
                Log.d(TAG, "Auth state changed - from: $currentUserId to: $newUserId")
                currentUserId = newUserId
                
                if (newUserId != null) {
                    // User logged in - sync Firestore data to local cache
                    viewModelScope.launch {
                        recentlyPlayedRepository.syncRecentlyPlayedFromFirestore(limit = 6)
                    }
                } else {
                    // User logged out - clear local data
                    _recentlyPlayedSongs.value = emptyList()
                    viewModelScope.launch {
                        recentlyPlayedRepository.clearCurrentUserHistory()
                    }
                }
            }
        }
    }

    private fun observeRecentlyPlayed() {
        viewModelScope.launch {
            recentlyPlayedRepository.getRecentTracks(limit = 6).collect { tracks ->
                _recentlyPlayedSongs.value = tracks.take(6)
            }
        }
    }

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
        if (hasLoaded && isTtlValid(lastHomeLoadTimestampMs)) return
        hasLoaded = true

        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            
            try {
                val homeResult = repository.getHomeData()
                val moodCategoriesResult = repository.getMoodCategories()

                homeResult.fold(
                    onSuccess = { data ->
                        val effectiveTrending = if (data.trendingData.getAllSongs().isNotEmpty()) {
                            data.trendingData.getAllSongs()
                        } else {
                            data.trending
                        }

                        _uiState.value = HomeUiState.Success(
                            trendingData = data.trendingData,
                            trending = effectiveTrending,
                            trendingPlaylists = emptyList(),
                            moodCategories = moodCategoriesResult.getOrDefault(emptyList()),
                            moodPlaylists = emptyList(),
                            selectedMoodTitle = "",
                            recommendations = emptyList(),
                            collaborativeRecommendations = emptyList(),
                            collaborativeTitle = CF_SECTION_TITLE,
                            collaborative = emptyList()
                        )
                        _topArtists.value = repository.getTopArtists(limit = 10).getOrDefault(emptyList())
                        lastHomeLoadTimestampMs = System.currentTimeMillis()
                        // Keep isRecommendationsLoading = true so the shimmer stays
                        // until loadRecommendationsIfNeeded() delivers personalized results.
                        _sectionLoadingState.value = _sectionLoadingState.value.copy(
                            isTrendingLoading = false,
                            isCollaborativeLoading = true
                        )

                        // Fetch collaborative recommendations after main home content is rendered.
                        loadCollaborativeRecommendations(forceRefresh = false)
                    },
                    onFailure = { error ->
                        _uiState.value = HomeUiState.Error(error.message ?: "Failed to load home data")
                        _sectionLoadingState.value = SectionLoadingState(
                            isTrendingLoading = false,
                            isRecommendationsLoading = false,
                            isTopArtistsLoading = false,
                            isCollaborativeLoading = false
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Failed to load home data")
                _sectionLoadingState.value = SectionLoadingState(
                    isTrendingLoading = false,
                    isRecommendationsLoading = false,
                    isTopArtistsLoading = false,
                    isCollaborativeLoading = false
                )
            }
        }
    }

    fun loadRecommendationsIfNeeded() {
        if (hasLoadedRecommendations && isTtlValid(lastRecommendationsLoadTimestampMs)) return
        hasLoadedRecommendations = true

        viewModelScope.launch {
            // Always fetch personalized recommendations — do not skip if non-empty.
            // This ensures Firestore-based content replaces any initial placeholder.
            _recommendedSongs.value = repository.fetchUserRecommendations()
            if (_topArtists.value.isEmpty()) {
                _topArtists.value = repository.getTopArtists(limit = 10).getOrDefault(emptyList())
            }
            lastRecommendationsLoadTimestampMs = System.currentTimeMillis()
            _sectionLoadingState.value = _sectionLoadingState.value.copy(
                isRecommendationsLoading = false,
                isTopArtistsLoading = false
            )
        }
    }

    fun clearRecommendations() {
        _recommendedSongs.value = emptyList()
        _topArtists.value = emptyList()
        hasLoadedRecommendations = false
        hasLoadedCollaborative = false
    }

    fun refreshHome(forceRefresh: Boolean = true) {
        if (_isRefreshing.value) return

        viewModelScope.launch {
            refreshHomeData(forceRefresh = forceRefresh)
            // Also refresh personalized recommendations after home data refreshes.
            hasLoadedRecommendations = false
            lastRecommendationsLoadTimestampMs = 0L
            loadRecommendationsIfNeeded()
        }
    }

    /**
     * Force refresh home data - bypasses hasLoaded guard
     * Used for pull-to-refresh functionality
     * 
     * @return true if refresh completed successfully, false otherwise
     */
    suspend fun refreshHomeData(forceRefresh: Boolean = true): Boolean {
        // Prevent concurrent refresh calls
        if (_isRefreshing.value) {
            Log.d(TAG, "Refresh already in progress, skipping")
            return false
        }

        return try {
            _isRefreshing.value = true

            // Sync recently played songs from Firestore first
            try {
                recentlyPlayedRepository.syncRecentlyPlayedFromFirestore(limit = 6, forceRefresh = forceRefresh)
                Log.d(TAG, "Recently played synced successfully during refresh")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync recently played during refresh: ${e.message}", e)
            }
            
            val homeResult = repository.getHomeData(forceRefresh = forceRefresh)
            val moodCategoriesResult = repository.getMoodCategories()

            var refreshSuccess = false
            homeResult.fold(
                onSuccess = { data ->
                    val effectiveTrending = if (data.trendingData.getAllSongs().isNotEmpty()) {
                        data.trendingData.getAllSongs()
                    } else {
                        data.trending
                    }

                    _uiState.value = HomeUiState.Success(
                        trendingData = data.trendingData,
                        trending = effectiveTrending,
                        trendingPlaylists = emptyList(),
                        moodCategories = moodCategoriesResult.getOrElse { emptyList() },
                        moodPlaylists = emptyList(),
                        selectedMoodTitle = "",
                        recommendations = emptyList(),
                        collaborativeRecommendations = emptyList(),
                        collaborativeTitle = CF_SECTION_TITLE,
                        collaborative = emptyList()
                    )
                    _topArtists.value = repository.getTopArtists(limit = 10).getOrDefault(emptyList())
                    hasLoadedCollaborative = false
                    lastCollaborativeLoadTimestampMs = 0L
                    loadCollaborativeRecommendations(forceRefresh = forceRefresh)
                    refreshSuccess = true
                    Log.d(TAG, "Home data refreshed successfully")
                },
                onFailure = { e ->
                    Log.e(TAG, "Refresh failed: ${e.message}", e)
                    refreshSuccess = false
                }
            )

            refreshSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Refresh error: ${e.message}", e)
            false
        } finally {
            _isRefreshing.value = false
        }
    }

    fun selectMood(category: MoodCategory) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is HomeUiState.Success) return@launch
            
            // Load mood playlists for selected category
            val result = repository.getMoodPlaylists(category.mood)
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

