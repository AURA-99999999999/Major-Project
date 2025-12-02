package com.aura.music.data.model

data class User(
    val id: String,
    val username: String,
    val email: String,
    val likedSongs: List<Song> = emptyList(),
    val recentlyPlayed: List<Song> = emptyList()
)

