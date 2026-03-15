package com.aura.music.data.model

data class UserPlaylist(
    val id: String,
    val name: String,
    val songCount: Int = 0,
    val createdAt: Long? = null
)

data class PlaylistSong(
    val videoId: String = "",
    val title: String = "",
    val album: String = "",
    val artists: List<String> = emptyList(),
    val thumbnail: String = "",
    val addedAt: Long? = null
) {
    fun toSong(): Song {
        val primaryArtist = artists.firstOrNull()
        return Song(
            videoId = videoId,
            title = title,
            artist = primaryArtist,
            artists = if (artists.isNotEmpty()) artists else null,
            thumbnail = thumbnail.ifBlank { null },
            album = album.ifBlank { null }
        )
    }
}
