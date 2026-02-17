package com.aura.music.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.model.Song
import com.aura.music.data.repository.FirestoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * PlayerViewModel - Shared playback state holder
 *
 * Single source of truth for mini player state across the entire app.
 *
 * Responsibilities:
 * - Holds currentSong and isPlaying state for all screens to consume
 * - Manages lastPlayed persistence to Firestore
 * - Connects to MusicService to keep state in sync
 *
 * Architecture:
 * - Exposedto UI via StateFlow (read-only)
 * - State is set by MusicService through public functions
 * - No screen directly updates state
 * - Multiple screens can observe without recreation
 *
 * Lifecycle:
 * - Created once in MainActivity
 * - Shared across all screens
 * - Survives screen recreation
 * - Cleared only on logout
 */
class PlayerViewModel(
    private val firestoreRepository: FirestoreRepository = FirestoreRepository()
) : ViewModel() {

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _lastPlayedSong = MutableStateFlow<Song?>(null)
    val lastPlayedSong: StateFlow<Song?> = _lastPlayedSong.asStateFlow()

    companion object {
        private const val TAG = "PlayerViewModel"
    }

    /**
     * Updates current song when playback starts/changes
     * Used by MusicService's MediaSession callback
     */
    fun updateCurrentSong(song: Song?) {
        _currentSong.update { song }
        if (song != null) {
            // Persist to Firestore in background
            persistLastPlayedSong(song)
        }
    }

    /**
     * Updates playing state when playback starts/pauses
     * Used by MusicService's onIsPlayingChanged callback
     */
    fun updateIsPlaying(isPlaying: Boolean) {
        _isPlaying.update { isPlaying }
        Log.d(TAG, "updateIsPlaying() isPlaying=$isPlaying currentSong=${_currentSong.value?.title}")
    }

    /**
     * Loads the last played song from Firestore
     * Called on login to restore mini player state
     */
    fun loadLastPlayedSong() {
        viewModelScope.launch {
            try {
                val lastPlayed = firestoreRepository.getLastPlayedSong()
                _lastPlayedSong.update { lastPlayed }
                // Don't set as current song - just show metadata in mini player
                Log.d(TAG, "✓ Last played song loaded: ${lastPlayed?.title}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load last played song", e)
            }
        }
    }

    /**
     * Clears all player state (on logout)
     */
    fun clearState() {
        _currentSong.update { null }
        _isPlaying.update { false }
        _lastPlayedSong.update { null }
        Log.d(TAG, "Player state cleared")
    }

    /**
     * Persists currently playing song to Firestore as lastPlayed
     * Called whenever a song starts playing
     */
    private fun persistLastPlayedSong(song: Song) {
        viewModelScope.launch {
            try {
                firestoreRepository.updateLastPlayedSong(song)
                Log.d(TAG, "✓ Last played song persisted: ${song.title}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist last played song", e)
                // Fail silently - not critical to mini player functionality
            }
        }
    }
}
