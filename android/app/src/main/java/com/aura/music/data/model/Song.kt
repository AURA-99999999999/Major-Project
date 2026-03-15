package com.aura.music.data.model

data class Song(
    val videoId: String,
    val title: String,
    val artist: String? = null,
    val artists: List<String>? = null,
    val thumbnail: String? = null,
    val duration: String? = null,
    val url: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val artistId: String? = null,
    val playCount: Int = 0,
    val language: String = "unknown",
    val year: String = "unknown",
    val starring: String? = null,
    // Epoch millis from backend play history (nullable for non-history contexts)
    val lastPlayedAt: Long? = null
) {
    fun getArtistString(): String {
        return artists?.joinToString(", ") ?: artist ?: "Unknown Artist"
    }
}

fun Song.withFallbackMetadata(fallback: Song): Song {
    return copy(
        title = title.ifBlank { fallback.title },
        artist = artist ?: fallback.artist,
        artists = if (!artists.isNullOrEmpty()) artists else fallback.artists,
        starring = starring ?: fallback.starring,
        thumbnail = thumbnail ?: fallback.thumbnail,
        duration = duration ?: fallback.duration,
        album = album ?: fallback.album,
        url = url ?: fallback.url
    )
}

