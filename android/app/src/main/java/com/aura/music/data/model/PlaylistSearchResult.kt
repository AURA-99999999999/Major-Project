package com.aura.music.data.model

/**
 * Playlist model for search results (different from user playlists)
 */
data class PlaylistSearchResult(
    val browseId: String,
    val playlistId: String,
    val title: String,
    val author: String,
    val thumbnail: String?,
    val itemCount: String? // e.g., "50 songs"
)
