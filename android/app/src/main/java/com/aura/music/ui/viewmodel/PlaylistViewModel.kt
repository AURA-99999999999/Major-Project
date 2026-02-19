package com.aura.music.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.model.PlaylistSong
import com.aura.music.data.model.Song
import com.aura.music.data.model.UserPlaylist
import com.aura.music.data.repository.MusicRepository
import com.aura.music.data.repository.PlaylistRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

data class PlaylistUiState(
    val playlists: List<UserPlaylist> = emptyList(),
    val currentPlaylist: UserPlaylist? = null,
    val songs: List<PlaylistSong> = emptyList(),
    val isLoading: Boolean = false,
    val isPlaybackPreparing: Boolean = false,
    val error: String? = null
)

sealed interface PlaylistEvent {
    data class ShowMessage(val message: String) : PlaylistEvent
    data class PlayQueue(val songs: List<Song>, val startIndex: Int) : PlaylistEvent
}

class PlaylistViewModel(
    private val playlistRepository: PlaylistRepository,
    private val musicRepository: MusicRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PlaylistEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<PlaylistEvent> = _events.asSharedFlow()

    private var playlistsJob: Job? = null
    private var playlistJob: Job? = null
    private var songsJob: Job? = null

    fun observePlaylists() {
        playlistsJob?.cancel()
        playlistsJob = viewModelScope.launch {
            playlistRepository.observePlaylists()
                .onStart {
                    _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                }
                .onEach { playlists ->
                    _uiState.value = _uiState.value.copy(
                        playlists = playlists,
                        isLoading = false
                    )
                }
                .catch { e ->
                    Log.e(TAG, "observePlaylists() failed", e)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load playlists"
                    )
                }
                .collect { }
        }
    }

    fun observePlaylistDetails(playlistId: String) {
        playlistJob?.cancel()
        songsJob?.cancel()

        playlistJob = viewModelScope.launch {
            playlistRepository.observePlaylist(playlistId)
                .onStart {
                    _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                }
                .onEach { playlist ->
                    _uiState.value = _uiState.value.copy(
                        currentPlaylist = playlist,
                        isLoading = false
                    )
                }
                .catch { e ->
                    Log.e(TAG, "observePlaylistDetails() failed", e)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load playlist"
                    )
                }
                .collect { }
        }

        songsJob = viewModelScope.launch {
            playlistRepository.observePlaylistSongs(playlistId)
                .onEach { songs ->
                    _uiState.value = _uiState.value.copy(songs = songs)
                }
                .catch { e ->
                    Log.e(TAG, "observePlaylistSongs() failed", e)
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Failed to load songs"
                    )
                }
                .collect { }
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            playlistRepository.createPlaylist(name)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _events.tryEmit(PlaylistEvent.ShowMessage("Playlist created"))
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to create playlist"
                    )
                    _events.tryEmit(PlaylistEvent.ShowMessage(_uiState.value.error ?: "Failed"))
                }
        }
    }

    fun renamePlaylist(playlistId: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            playlistRepository.renamePlaylist(playlistId, name)
                .onSuccess {
                    _events.tryEmit(PlaylistEvent.ShowMessage("Playlist renamed"))
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Failed to rename playlist"
                    )
                    _events.tryEmit(PlaylistEvent.ShowMessage(_uiState.value.error ?: "Failed"))
                }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
                .onSuccess {
                    _events.tryEmit(PlaylistEvent.ShowMessage("Playlist deleted"))
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Failed to delete playlist"
                    )
                    _events.tryEmit(PlaylistEvent.ShowMessage(_uiState.value.error ?: "Failed"))
                }
        }
    }

    fun addSongToPlaylist(playlistId: String, song: Song) {
        viewModelScope.launch {
            playlistRepository.addSongToPlaylist(playlistId, song)
                .onSuccess {
                    _events.tryEmit(PlaylistEvent.ShowMessage("Added to playlist"))
                }
                .onFailure { e ->
                    val message = e.message ?: "Failed to add song"
                    _uiState.value = _uiState.value.copy(error = message)
                    _events.tryEmit(PlaylistEvent.ShowMessage(message))
                }
        }
    }

    fun removeSongFromPlaylist(playlistId: String, videoId: String) {
        viewModelScope.launch {
            playlistRepository.removeSongFromPlaylist(playlistId, videoId)
                .onSuccess {
                    _events.tryEmit(PlaylistEvent.ShowMessage("Removed from playlist"))
                }
                .onFailure { e ->
                    val message = e.message ?: "Failed to remove song"
                    _uiState.value = _uiState.value.copy(error = message)
                    _events.tryEmit(PlaylistEvent.ShowMessage(message))
                }
        }
    }

    fun prepareSongForPlayback(song: PlaylistSong) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPlaybackPreparing = true, error = null)

            val songs = _uiState.value.songs.map { it.toSong() }
            val index = songs.indexOfFirst { it.videoId == song.videoId }
            if (index < 0) {
                _uiState.value = _uiState.value.copy(isPlaybackPreparing = false)
                return@launch
            }

            _uiState.value = _uiState.value.copy(isPlaybackPreparing = false)
            _events.tryEmit(PlaylistEvent.PlayQueue(songs, index))
        }
    }

    companion object {
        private const val TAG = "PlaylistViewModel"
    }
}

