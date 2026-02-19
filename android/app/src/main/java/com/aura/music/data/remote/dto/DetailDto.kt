package com.aura.music.data.remote.dto

import com.aura.music.data.model.Song
import com.google.gson.annotations.SerializedName

// Album Detail Response
data class AlbumDetailResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("album") val album: AlbumDetailDto? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("cached") val cached: Boolean? = false
)

data class AlbumDetailDto(
    @SerializedName("albumId") val albumId: String,
    @SerializedName("browseId") val browseId: String,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("artists") val artists: List<String>,
    @SerializedName("thumbnail") val thumbnail: String,
    @SerializedName("year") val year: Int? = null,
    @SerializedName("trackCount") val trackCount: Int,
    @SerializedName("duration") val duration: String? = null,
    @SerializedName("songs") val songs: List<Song>
)

// Artist Detail Response
data class ArtistDetailResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("artist") val artist: ArtistDetailDto? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("cached") val cached: Boolean? = false
)

data class ArtistDetailDto(
    @SerializedName("artistId") val artistId: String,
    @SerializedName("name") val name: String,
    @SerializedName("thumbnail") val thumbnail: String,
    @SerializedName("subscribers") val subscribers: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("topSongs") val topSongs: List<Song>,
    @SerializedName("albums") val albums: List<ArtistAlbumDto>
)

data class ArtistAlbumDto(
    @SerializedName("browseId") val browseId: String,
    @SerializedName("title") val title: String,
    @SerializedName("thumbnail") val thumbnail: String,
    @SerializedName("year") val year: Int? = null,
    @SerializedName("type") val type: String? = null
)

// Playlist Detail Response
data class PlaylistDetailResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("playlist") val playlist: PlaylistDetailDto? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("cached") val cached: Boolean? = false
)

data class PlaylistDetailDto(
    @SerializedName("playlistId") val playlistId: String,
    @SerializedName("browseId") val browseId: String,
    @SerializedName("title") val title: String,
    @SerializedName("author") val author: String,
    @SerializedName("thumbnail") val thumbnail: String,
    @SerializedName("trackCount") val trackCount: Int,
    @SerializedName("duration") val duration: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("songs") val songs: List<Song>
)
