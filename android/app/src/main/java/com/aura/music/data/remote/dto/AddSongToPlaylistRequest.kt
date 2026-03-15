package com.aura.music.data.remote.dto

/**
 * Concrete request models for add-to-playlist endpoint.
 * Avoids Retrofit wildcard generic issues from raw Map request bodies.
 */
data class AddSongToPlaylistRequest(
    val userId: String,
    val song: PlaylistSongRequest
)

data class PlaylistSongRequest(
    val videoId: String,
    val title: String,
    val artist: String,
    val artists: List<String>,
    val starring: String,
    val thumbnail: String,
    val duration: String,
    val url: String,
    val album: String,
    val artistId: String
)
