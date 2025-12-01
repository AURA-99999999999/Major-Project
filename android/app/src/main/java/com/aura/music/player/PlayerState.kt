package com.aura.music.player

import com.aura.music.data.model.Song

data class PlayerState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val isLoading: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val volume: Float = 1.0f,
    val queue: List<Song> = emptyList(),
    val history: List<Song> = emptyList(),
    val error: String? = null
)

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

