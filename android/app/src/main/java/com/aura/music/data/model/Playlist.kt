package com.aura.music.data.model

data class Playlist(
    val id: String,
    val name: String,
    val description: String? = null,
    val userId: String,
    val songs: List<Song> = emptyList(),
    val coverImage: String? = null,
    val createdAt: String? = null
)

