package com.aura.music.data.model

data class PlaylistDetail(
    val id: String,
    val title: String,
    val description: String = "",
    val thumbnail: String = "",
    val author: String = "YouTube Music",
    val songCount: Int,
    val songs: List<Song> = emptyList()
)
