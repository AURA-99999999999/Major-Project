package com.aura.music.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.model.Song
import com.aura.music.data.repository.FirestoreRepository
import com.aura.music.data.repository.MusicRepository
import com.google.firebase.auth.FirebaseAuth
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

data class LikedSongsUiState(
    val songs: List<Song> = emptyList(),
    val likedSongIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val isPlaybackPreparing: Boolean = false,
    val error: String? = null
)

sealed interface LikedSongsEvent {
    data class ShowMessage(val message: String) : LikedSongsEvent
    data class PlaySong(val song: Song) : LikedSongsEvent
}

class LikedSongsViewModel(
    private val firestoreRepository: FirestoreRepository,
    private val musicRepository: MusicRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {
    private val _uiState = MutableStateFlow(LikedSongsUiState())
    val uiState: StateFlow<LikedSongsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LikedSongsEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<LikedSongsEvent> = _events.asSharedFlow()

    private var likedSongsJob: Job? = null

    fun observeLikedSongs() {
        val userId = auth.currentUser?.uid
        if (userId.isNullOrBlank()) {
            _uiState.value = LikedSongsUiState()
            return
        }

        likedSongsJob?.cancel()
        likedSongsJob = viewModelScope.launch {
            firestoreRepository.getLikedSongs(userId)
                .onStart {
                    _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                }
                .onEach { songs ->
                    _uiState.value = _uiState.value.copy(
                        songs = songs,
                        likedSongIds = songs.map { it.videoId }.toSet(),
                        isLoading = false
                    )
                }
                .catch { e ->
                    Log.e(TAG, "observeLikedSongs() failed", e)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load liked songs"
                    )
                    _events.tryEmit(
                        LikedSongsEvent.ShowMessage(_uiState.value.error ?: "Failed to load liked songs")
                    )
                }
                .collect { }
        }
    }

    fun isSongLiked(videoId: String): Boolean {
        return _uiState.value.likedSongIds.contains(videoId)
    }

    fun toggleLike(song: Song) {
        if (isSongLiked(song.videoId)) {
            removeFromLikedSongs(song.videoId)
        } else {
            addToLikedSongs(song)
        }
    }

    fun addToLikedSongs(song: Song) {
        val userId = auth.currentUser?.uid
        if (userId.isNullOrBlank()) {
            _events.tryEmit(LikedSongsEvent.ShowMessage("Please sign in to like songs"))
            return
        }

        viewModelScope.launch {
            firestoreRepository.addToLikedSongs(userId, song)
                .onSuccess {
                    _events.tryEmit(LikedSongsEvent.ShowMessage("Added to Liked Songs"))
                }
                .onFailure { e ->
                    _events.tryEmit(
                        LikedSongsEvent.ShowMessage(e.message ?: "Failed to like song")
                    )
                }
        }
    }

    fun removeFromLikedSongs(videoId: String) {
        val userId = auth.currentUser?.uid
        if (userId.isNullOrBlank()) {
            _events.tryEmit(LikedSongsEvent.ShowMessage("Please sign in to manage liked songs"))
            return
        }

        viewModelScope.launch {
            firestoreRepository.removeFromLikedSongs(userId, videoId)
                .onSuccess {
                    _events.tryEmit(LikedSongsEvent.ShowMessage("Removed from Liked Songs"))
                }
                .onFailure { e ->
                    _events.tryEmit(
                        LikedSongsEvent.ShowMessage(e.message ?: "Failed to remove song")
                    )
                }
        }
    }

    fun prepareSongForPlayback(song: Song) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPlaybackPreparing = true, error = null)

            val resolvedSong = musicRepository.getSong(song.videoId)
                .getOrNull()
                ?.let { mergeMetadataFromFallback(it, song) }

            if (resolvedSong == null || resolvedSong.url.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(isPlaybackPreparing = false)
                _events.tryEmit(LikedSongsEvent.ShowMessage("Stream URL missing for ${song.title}"))
                return@launch
            }

            _uiState.value = _uiState.value.copy(isPlaybackPreparing = false)
            _events.tryEmit(LikedSongsEvent.PlaySong(resolvedSong))
        }
    }

    private fun mergeMetadataFromFallback(resolved: Song, fallback: Song): Song {
        return resolved.copy(
            title = if (fallback.title.isNotBlank()) fallback.title else resolved.title,
            artist = fallback.artist ?: resolved.artist,
            artists = if (!fallback.artists.isNullOrEmpty()) fallback.artists else resolved.artists,
            thumbnail = fallback.thumbnail ?: resolved.thumbnail,
            album = fallback.album ?: resolved.album
        )
    }

    companion object {
        private const val TAG = "LikedSongsViewModel"
    }
}
