package com.aura.music.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.model.Song
import com.aura.music.data.remote.dto.ArtistAlbumDto
import com.aura.music.data.repository.MusicRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ArtistData(
    val artistId: String,
    val name: String,
    val thumbnail: String,
    val subscribers: String?,
    val description: String?,
    val topSongs: List<Song>,
    val albums: List<ArtistAlbumDto>
)

data class ArtistDetailUiState(
    val artist: ArtistData? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface ArtistDetailEvent {
    data class PlayQueue(val songs: List<Song>, val startIndex: Int) : ArtistDetailEvent
    data class NavigateToAlbum(val browseId: String) : ArtistDetailEvent
    data class ShowMessage(val message: String) : ArtistDetailEvent
}

class ArtistDetailViewModel(
    private val repository: MusicRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ArtistDetailUiState())
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()
    
    private val _events = MutableSharedFlow<ArtistDetailEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ArtistDetailEvent> = _events
    
    fun loadArtist(browseId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                repository.getArtistDetails(browseId).fold(
                    onSuccess = { artistDto ->
                        val artist = ArtistData(
                            artistId = artistDto.artistId,
                            name = artistDto.name,
                            thumbnail = artistDto.thumbnail,
                            subscribers = artistDto.subscribers,
                            description = artistDto.description,
                            topSongs = artistDto.topSongs,
                            albums = artistDto.albums
                        )
                        
                        _uiState.update { 
                            it.copy(
                                artist = artist,
                                isLoading = false,
                                error = null
                            ) 
                        }
                        
                        Log.d(TAG, "Artist loaded: ${artist.name}")
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to load artist: ${error.message}")
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = "Failed to load artist"
                            ) 
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading artist", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "An error occurred"
                    ) 
                }
            }
        }
    }
    
    fun playSongFromArtist(index: Int) {
        val artist = _uiState.value.artist ?: return
        
        if (index < 0 || index >= artist.topSongs.size) {
            Log.w(TAG, "Invalid song index: $index")
            return
        }
        
        viewModelScope.launch {
            _events.emit(ArtistDetailEvent.PlayQueue(artist.topSongs, index))
        }
    }
    
    fun openAlbum(browseId: String) {
        viewModelScope.launch {
            _events.emit(ArtistDetailEvent.NavigateToAlbum(browseId))
        }
    }
    
    companion object {
        private const val TAG = "ArtistDetailViewModel"
    }
}

// YTPlaylist Detail ViewModel
data class YTPlaylistData(
    val playlistId: String,
    val browseId: String,
    val title: String,
    val author: String,
    val thumbnail: String,
    val trackCount: Int,
    val description: String?,
    val songs: List<Song>
)

data class YTPlaylistDetailUiState(
    val playlist: YTPlaylistData? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface YTPlaylistDetailEvent {
    data class PlayQueue(val songs: List<Song>, val startIndex: Int) : YTPlaylistDetailEvent
    data class ShowMessage(val message: String) : YTPlaylistDetailEvent
}

class YTPlaylistDetailViewModel(
    private val repository: MusicRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(YTPlaylistDetailUiState())
    val uiState: StateFlow<YTPlaylistDetailUiState> = _uiState.asStateFlow()
    
    private val _events = MutableSharedFlow<YTPlaylistDetailEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<YTPlaylistDetailEvent> = _events
    
    fun loadPlaylist(browseId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                repository.getYTPlaylistDetails(browseId).fold(
                    onSuccess = { playlistDto ->
                        val playlist = YTPlaylistData(
                            playlistId = playlistDto.playlistId,
                            browseId = playlistDto.browseId,
                            title = playlistDto.title,
                            author = playlistDto.author,
                            thumbnail = playlistDto.thumbnail,
                            trackCount = playlistDto.trackCount,
                            description = playlistDto.description,
                            songs = playlistDto.songs
                        )
                        
                        _uiState.update { 
                            it.copy(
                                playlist = playlist,
                                isLoading = false,
                                error = null
                            ) 
                        }
                        
                        Log.d(TAG, "Playlist loaded: ${playlist.title}")
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to load playlist: ${error.message}")
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = "Failed to load playlist"
                            ) 
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading playlist", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "An error occurred"
                    ) 
                }
            }
        }
    }
    
    fun playSongFromPlaylist(index: Int) {
        val playlist = _uiState.value.playlist ?: return
        
        if (index < 0 || index >= playlist.songs.size) {
            Log.w(TAG, "Invalid song index: $index")
            return
        }
        
        viewModelScope.launch {
            _events.emit(YTPlaylistDetailEvent.PlayQueue(playlist.songs, index))
        }
    }
    
    companion object {
        private const val TAG = "YTPlaylistDetailVM"
    }
}
