package com.aura.music.data.model

/**
 * Search results grouped by category
 */
data class SearchResults(
    val songs: List<Song>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val playlists: List<PlaylistSearchResult>,
    val count: Int,
    val query: String
) {
    fun isEmpty(): Boolean = count == 0
    
    fun hasAnySongs(): Boolean = songs.isNotEmpty()
    fun hasAnyAlbums(): Boolean = albums.isNotEmpty()
    fun hasAnyArtists(): Boolean = artists.isNotEmpty()
    fun hasAnyPlaylists(): Boolean = playlists.isNotEmpty()
}
