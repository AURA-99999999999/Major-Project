package com.aura.music.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.model.Song
/**
 * Playback-specific ViewModel.
 *
 * Firestore logging is handled in MusicService when playback actually starts,
 * so this ViewModel does not perform any logging.
 */
class PlayerViewModel : ViewModel() {

    companion object {
        private const val TAG = "PlayerViewModel"
    }

    /**
     * Call this whenever playback state changes.
     * Logging is centralized in MusicService (real playback lifecycle).
     */
    fun onPlaybackStateChanged(
        song: Song?,
        isPlaying: Boolean,
        positionMs: Long
    ) {
        Log.d(TAG, "onPlaybackStateChanged() isPlaying=$isPlaying positionMs=$positionMs song=${song?.videoId}")
    }
}
