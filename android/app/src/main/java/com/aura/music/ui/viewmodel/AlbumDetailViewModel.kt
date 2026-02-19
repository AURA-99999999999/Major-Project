package com.aura.music.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.model.Song
import com.aura.music.data.repository.MusicRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AlbumData(
    val albumId: String,
    val browseId: String,
    val title: String,
    val artist: String,
    val artists: List<String>,
    val thumbnail: String,
    val year: Int?,
    val trackCount: Int,
    val songs: List<Song>
)

data class AlbumDetailUiState(
    val album: AlbumData? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface AlbumDetailEvent {
    data class PlayQueue(val songs: List<Song>, val startIndex: Int) : AlbumDetailEvent
    data class ShowMessage(val message: String) : AlbumDetailEvent
}

class AlbumDetailViewModel(
    private val repository: MusicRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()
    
    private val _events = MutableSharedFlow<AlbumDetailEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<AlbumDetailEvent> = _events
    
    fun loadAlbum(browseId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                repository.getAlbumDetails(browseId).fold(
                    onSuccess = { albumDto ->
                        val album = AlbumData(
                            albumId = albumDto.albumId,
                            browseId = albumDto.browseId,
                            title = albumDto.title,
                            artist = albumDto.artist,
                            artists = albumDto.artists,
                            thumbnail = albumDto.thumbnail,
                            year = albumDto.year,
                            trackCount = albumDto.trackCount,
                            songs = albumDto.songs
                        )
                        
                        _uiState.update { 
                            it.copy(
                                album = album,
                                isLoading = false,
                                error = null
                            ) 
                        }
                        
                        Log.d(TAG, "Album loaded: ${album.title} - ${album.songs.size} songs")
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to load album: ${error.message}")
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = "Failed to load album"
                            ) 
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading album", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "An error occurred"
                    ) 
                }
            }
        }
    }
    
    fun playSongFromAlbum(index: Int) {
        val album = _uiState.value.album ?: return
        
        if (index < 0 || index >= album.songs.size) {
            Log.w(TAG, "Invalid song index: $index")
            return
        }
        
        viewModelScope.launch {
            _events.emit(AlbumDetailEvent.PlayQueue(album.songs, index))
        }
    }
    
    companion object {
        private const val TAG = "AlbumDetailViewModel"
    }
}
