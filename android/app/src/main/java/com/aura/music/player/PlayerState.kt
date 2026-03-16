package com.aura.music.player

import com.aura.music.data.model.Song

enum class PlaybackUiState {
    IDLE,
    LOADING,
    PLAYING,
    PAUSED,
    ERROR,
}

data class PlayerState(
    val currentSong: Song? = null,
    val playbackSource: String? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val isLoading: Boolean = false,
    val uiState: PlaybackUiState = PlaybackUiState.IDLE,
    val repeatMode: RepeatMode = RepeatMode.NONE,
    val shuffleEnabled: Boolean = false,
    val volume: Float = 1.0f,
    val error: String? = null
)

